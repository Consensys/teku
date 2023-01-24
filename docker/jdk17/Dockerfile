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

RUN apt-get -y update && apt-get -y upgrade && apt-get -y install curl
RUN rm -rf /var/lib/api/lists/*
RUN adduser --disabled-password --gecos "" --home /opt/teku teku && \
    chown teku:teku /opt/teku

USER teku
WORKDIR /opt/teku

# copy application (with libraries inside)
COPY --chown=teku:teku teku /opt/teku/

# Default to UTF-8 locale
ENV LANG C.UTF-8

ENV TEKU_REST_API_INTERFACE="0.0.0.0"
ENV TEKU_VALIDATOR_API_INTERFACE="0.0.0.0"
ENV TEKU_METRICS_INTERFACE="0.0.0.0"

# List Exposed Ports
# Metrics, Rest API, LibP2P, Discv5
EXPOSE 8008 5051 9000 9000/udp

# specify default command
ENTRYPOINT ["/opt/teku/bin/teku"]


# Build-time metadata as defined at http://label-schema.org
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="Teku" \
      org.label-schema.description="Ethereum 2.0 Beacon Chain Client" \
      org.label-schema.url="https://consensys.net/knowledge-base/ethereum-2/teku/" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/ConsenSys/teku.git" \
      org.label-schema.vendor="ConsenSys" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"
