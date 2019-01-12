# Base Alpine Linux based image with OpenJDK JRE only
#FROM openjdk:8-jre-alpine
FROM openjdk:8-jdk

# copy application (with libraries inside)
ADD build/install/artemis /opt/artemis/
ADD integration-tests/src/test/resources/net/consensys/artemis/tests/cluster/docker/geth/genesis.json /opt/artemis/genesis.json

# List Exposed Ports
EXPOSE 8084 8545 30303 30303/udp

# specify default command
ENTRYPOINT ["/opt/artemis/bin/artemis"]
