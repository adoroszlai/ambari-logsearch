/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ambari.logfeeder.loglevelfilter;

import com.google.gson.Gson;
import org.apache.ambari.logfeeder.common.LogFeederConstants;
import org.apache.ambari.logfeeder.conf.LogFeederProps;
import org.apache.ambari.logfeeder.plugin.input.InputMarker;
import org.apache.ambari.logfeeder.util.LogFeederUtil;
import org.apache.ambari.logsearch.config.api.LogLevelFilterMonitor;
import org.apache.ambari.logsearch.config.api.LogSearchConfig;
import org.apache.ambari.logsearch.config.api.model.loglevelfilter.LogLevelFilter;
import org.apache.ambari.logsearch.config.zookeeper.LogLevelFilterManagerZK;
import org.apache.ambari.logsearch.config.zookeeper.LogSearchConfigZKHelper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manage log level filter object and cache them. (in memory)
 */
public class LogLevelFilterHandler implements LogLevelFilterMonitor {
  private static final Logger logger = LogManager.getLogger(LogLevelFilterHandler.class);

  private static final String TIMEZONE = "GMT";
  private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";

  private static final boolean DEFAULT_VALUE = true;

  private static ThreadLocal<DateFormat> formatter = new ThreadLocal<DateFormat>() {
    protected DateFormat initialValue() {
      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
      dateFormat.setTimeZone(TimeZone.getTimeZone(TIMEZONE));
      return dateFormat;
    }
  };

  @Inject
  private LogFeederProps logFeederProps;

  private LogSearchConfig config;
  private Map<String, LogLevelFilter> filters = new ConcurrentHashMap<>();

  // Use these 2 only if local config is used with zk log level filter storage
  private TreeCache clusterCache = null;
  private TreeCacheListener listener = null;

  public LogLevelFilterHandler(LogSearchConfig config) {
    this.config = config;
  }

  @PostConstruct
  public void init() throws Exception {
    TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE));
    if (logFeederProps.isZkFilterStorage() && logFeederProps.isUseLocalConfigs()) {
      LogLevelFilterManagerZK filterManager = (LogLevelFilterManagerZK) config.getLogLevelFilterManager();
      CuratorFramework client = filterManager.getClient();
      client.start();
      Gson gson = filterManager.getGson();
      LogSearchConfigZKHelper.waitUntilRootAvailable(client);
      TreeCache clusterCache = LogSearchConfigZKHelper.createClusterCache(client, logFeederProps.getClusterName());
      TreeCacheListener listener = LogSearchConfigZKHelper.createTreeCacheListener(
        logFeederProps.getClusterName(), gson, this);
      LogSearchConfigZKHelper.addAndStartListenersOnCluster(clusterCache, listener);
    }
    if (config.getLogLevelFilterManager() != null) {
      TreeMap<String, LogLevelFilter> sortedFilters = config.getLogLevelFilterManager()
        .getLogLevelFilters(logFeederProps.getClusterName())
        .getFilter();
      filters = new ConcurrentHashMap<>(sortedFilters);
    }
  }

  @Override
  public void setLogLevelFilter(String logId, LogLevelFilter logLevelFilter) {
    synchronized (LogLevelFilterHandler.class) {
      filters.put(logId, logLevelFilter);
    }
  }

  @Override
  public void removeLogLevelFilter(String logId) {
    synchronized (LogLevelFilterHandler.class) {
      filters.remove(logId);
    }
  }

  @Override
  public Map<String, LogLevelFilter> getLogLevelFilters() {
    return filters;
  }

  public boolean isAllowed(String hostName, String logId, String level, List<String> defaultLogLevels) {
    if (!logFeederProps.isLogLevelFilterEnabled()) {
      return true;
    }

    LogLevelFilter logFilter = findLogFilter(logId, defaultLogLevels);
    List<String> allowedLevels = getAllowedLevels(hostName, logFilter);
    return allowedLevels.isEmpty() || allowedLevels.contains(level);
  }

  public boolean isAllowed(String jsonBlock, InputMarker inputMarker, List<String> defaultLogLevels) {
    if (org.apache.commons.lang3.StringUtils.isEmpty(jsonBlock)) {
      return DEFAULT_VALUE;
    }
    Map<String, Object> jsonObj = LogFeederUtil.toJSONObject(jsonBlock);
    return isAllowed(jsonObj, inputMarker, defaultLogLevels);
  }

  public boolean isAllowed(Map<String, Object> jsonObj, InputMarker inputMarker, List<String> defaultLogLevels) {
    if ("audit".equals(inputMarker.getInput().getInputDescriptor().getRowtype()))
      return true;

    boolean isAllowed = applyFilter(jsonObj, defaultLogLevels);
    if (!isAllowed) {
      logger.trace("Filter block the content :" + LogFeederUtil.getGson().toJson(jsonObj));
    }
    return isAllowed;
  }


  public boolean applyFilter(Map<String, Object> jsonObj, List<String> defaultLogLevels) {
    if (MapUtils.isEmpty(jsonObj)) {
      logger.warn("Output jsonobj is empty");
      return DEFAULT_VALUE;
    }

    String hostName = (String) jsonObj.get(LogFeederConstants.SOLR_HOST);
    String logId = (String) jsonObj.get(LogFeederConstants.SOLR_COMPONENT);
    String level = (String) jsonObj.get(LogFeederConstants.SOLR_LEVEL);
    if (org.apache.commons.lang3.StringUtils.isNotBlank(hostName) && org.apache.commons.lang3.StringUtils.isNotBlank(logId) && org.apache.commons.lang3.StringUtils.isNotBlank(level)) {
      return isAllowed(hostName, logId, level, defaultLogLevels);
    } else {
      return DEFAULT_VALUE;
    }
  }

  private synchronized LogLevelFilter findLogFilter(String logId, List<String> defaultLogLevels) {
    LogLevelFilter logFilter = filters.get(logId);
    if (logFilter != null) {
      return logFilter;
    }

    logger.info("Filter is not present for log " + logId + ", creating default filter");
    LogLevelFilter defaultFilter = new LogLevelFilter();
    defaultFilter.setLabel(logId);
    defaultFilter.setDefaultLevels(defaultLogLevels);

    try {
      config.getLogLevelFilterManager().createLogLevelFilter(logFeederProps.getClusterName(), logId, defaultFilter);
      filters.put(logId, defaultFilter);
    } catch (Exception e) {
      logger.warn("Could not persist the default filter for log " + logId, e);
    }

    return defaultFilter;
  }

  private List<String> getAllowedLevels(String hostName, LogLevelFilter componentFilter) {
    String componentName = componentFilter.getLabel();
    List<String> hosts = componentFilter.getHosts();
    List<String> defaultLevels = componentFilter.getDefaultLevels();
    List<String> overrideLevels = componentFilter.getOverrideLevels();
    Date expiryTime = componentFilter.getExpiryTime();

    // check is user override or not
    if (expiryTime != null || CollectionUtils.isNotEmpty(overrideLevels) || CollectionUtils.isNotEmpty(hosts)) {
      if (CollectionUtils.isEmpty(hosts)) { // hosts list is empty or null consider it apply on all hosts
        hosts.add(LogFeederConstants.ALL);
      }

      if (hosts.isEmpty() || hosts.contains(hostName)) {
        if (isFilterExpired(componentFilter)) {
          logger.debug("Filter for component " + componentName + " and host :" + hostName + " is expired at " +
            componentFilter.getExpiryTime());
          return defaultLevels;
        } else {
          return overrideLevels;
        }
      }
    }
    return defaultLevels;
  }

  private boolean isFilterExpired(LogLevelFilter logLevelFilter) {
    if (logLevelFilter == null)
      return false;

    Date filterEndDate = logLevelFilter.getExpiryTime();
    if (filterEndDate == null) {
      return false;
    }

    Date currentDate = new Date();
    if (!currentDate.before(filterEndDate)) {
      logger.debug("Filter for  Component :" + logLevelFilter.getLabel() + " and Hosts : [" +
        StringUtils.join(logLevelFilter.getHosts(), ',') + "] is expired because of filter endTime : " +
        formatter.get().format(filterEndDate) + " is older than currentTime :" + formatter.get().format(currentDate));
      return true;
    } else {
      return false;
    }
  }

  public void setLogFeederProps(LogFeederProps logFeederProps) {
    this.logFeederProps = logFeederProps;
  }
}
