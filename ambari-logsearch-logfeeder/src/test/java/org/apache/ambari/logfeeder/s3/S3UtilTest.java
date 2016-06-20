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
package org.apache.ambari.logfeeder.s3;

import org.apache.log4j.Logger;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class S3UtilTest {
  private static final Logger LOG = Logger.getLogger(S3UtilTest.class);

  // @Test
  public void testS3Util_pathToBucketName() throws Exception {
    String s3Path = "s3://bucket_name/path/file.txt";
    String expectedBucketName = "bucket_name";
    String actualBucketName = S3Util.INSTANCE.getBucketName(s3Path);
    assertEquals(expectedBucketName, actualBucketName);
  }

  // @Test
  public void testS3Util_pathToS3Key() throws Exception {
    String s3Path = "s3://bucket_name/path/file.txt";
    String expectedS3key = "path/file.txt";
    String actualS3key = S3Util.INSTANCE.getS3Key(s3Path);
    assertEquals(expectedS3key, actualS3key);
  }

}
