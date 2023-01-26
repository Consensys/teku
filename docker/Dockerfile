FROM eclipse-temurin:17 as jre-build

# Create a custom Java runtime
RUN JAVA_TOOL_OPTIONS="-Djdk.lang.Process.launchMechanism=vfork" $JAVA_HOME/bin/jlink \
         --add-modules ALL-MODULE-PATH \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

FROM ubuntu:22.04
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

RUN apt-get -y update && apt-get -y upgrade && apt-get -y install curl git jq docker
RUN rm -rf /var/lib/api/lists/*
RUN adduser --disabled-password --gecos "" --home /opt/teku teku && \
    chown teku:teku /opt/teku

RUN git clone --depth 1 https://github.com/ConsenSys/teku.git --branch 4844-interop && \
    (cd teku && ./gradlew installDist)

# Default to UTF-8 locale
ENV LANG C.UTF-8

ENV TEKU_REST_API_INTERFACE="0.0.0.0"
ENV TEKU_VALIDATOR_API_INTERFACE="0.0.0.0"
ENV TEKU_METRICS_INTERFACE="0.0.0.0"

# List Exposed Ports
# Metrics, Rest API, LibP2P, Discv5
EXPOSE 8008 5051 9000 9000/udp

USER teku

# specify default command
ENTRYPOINT ["/teku/build/install/teku/bin/teku"]
