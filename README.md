# Apache Ambari Log Search
[![Build Status](https://builds.apache.org/buildStatus/icon?job=Ambari-LogSearch-master-Commit)](https://builds.apache.org/view/A/view/Ambari/job/Ambari-LogSearch-master-Commit/)
![license](http://img.shields.io/badge/license-Apache%20v2-blue.svg)

Log aggregation, analysis, and visualization for Ambari managed (and any other) services.

Log Search is a sub-project of [Apache Ambari](https://github.com/apache/ambari)

## Development

Requires JDK 8 (JDK 11 is recommended)

### Prerequisites

- Install [docker](https://docs.docker.com/)
- For Mac OS X use [Docker for Mac](https://docs.docker.com/docker-for-mac/)
- [Docker compose](https://docs.docker.com/compose/) is also required.

### Build and start Log Search in docker container
```bash
# to see available commands: run start-logsearch without arguments
cd docker
./logsearch-docker build-and-run # build mvn project locally, build docker image, start containers
```
If you run the script at first time, it will generate you a new `Profile` file or an `.env` file inside docker directory (run twice if both missing and you want to generate Profile and .env as well), in .env file you should set `MAVEN_REPOSITORY_LOCATION` (point to local maven repository location, it uses `~/.m2` by default). These will be used as volumes for the docker container. Profile file holds the environment variables that are used inside the containers, the .env file is used outside of the containers

Then you can use the `logsearch-docker` script to start the containers (`start` command).
Also you can use docker-compose manually to start/manage the containers.
```bash
docker-compose up -d
# or start all services in one container:
docker-compose -f all.yml up -d
```
After the logsearch container is started you can enter to it with following commands:
```bash
docker exec -it docker_logsearch_1 bash
# or if you used all.yml for starting the logsearch docker container:
docker exec -it logsearch bash
```
In case if you started the containers separately and if you would like to access Solr locally with through your external ZooKeeper container, then point `solr` to `localhost` in your `/etc/hosts` file.

### Run applications from IDE / maven

- [Start Log Search locally](ambari-logsearch-server/README.md)
- [Start Log Feeder locally](ambari-logsearch-logfeeder/README.md)

## Package build process

1. Check out the code from GIT repository

2. On the logsearch root folder (ambari/ambari-logsearch), please execute the following make command to build RPM/DPKG:
```bash
make rpm
# or for jdk11
export LOGSEARCH_JDK_11=true
make rpm
```
  or
```bash
make deb
# or for jdk11
export LOGSEARCH_JDK_11=true
make deb
```
3. Generated RPM/DPKG files will be found in ambari-logsearch-assembly/target folder

## Running Integration Tests

By default integration tests are not a part of the build process, you need to set -Dbackend-tests or -Dselenium-tests (or you can use -Dall-tests to run both). To running the tests you will need docker here as well (right now docker-for-mac and unix are supported by default, for boot2docker you need to pass -Ddocker.host parameter to the build).

```bash
# from ambari-logsearch folder
mvn clean integration-test -Dbackend-tests failsafe:verify
# or run selenium tests with docker for mac, but before that you nedd to start xquartz
open -a XQuartz
# then in an another window you can start ui tests
mvn clean integration-test -Dselenium-tests failsafe:verify
# you can specify story file folde location with -Dbackend.stories.location and -Dui.stories.location (absolute file path) in the commands
```
Also you can run from the IDE, but make sure all of the ambari logsearch modules are built.


## Update version (for release or specific builds)

```bash
make set-version new-version="2.8.0.0-11"
```

## Contributing

https://cwiki.apache.org/confluence/display/AMBARI/How+to+Contribute

(That is the ambari contribution guide, everything is the same here except use ambari-logsearch repository instead of ambari)

## License

- http://ambari.apache.org/license.html
- See more at [Ambari repository](https://github.com/apache/ambari)
