FROM eclipse-temurin:24 as jre-build

# Create a custom Java runtime
RUN JAVA_TOOL_OPTIONS="-Djdk.lang.Process.launchMechanism=vfork" $JAVA_HOME/bin/jlink \
         --add-modules java.base \
         --add-modules java.se \
         --add-modules java.management \
         --add-modules java.prefs \
         --add-modules jdk.httpserver \
         --add-modules jdk.unsupported \
         --add-modules jdk.jdwp.agent \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

FROM ubuntu:24.04
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

RUN apt-get -y update && apt-get -y upgrade && apt-get -y install curl libc-bin libc6 adduser && \
    # Clean apt cache
    apt-get clean && \
    rm -rf /var/cache/apt/archives/* && \
    rm -rf /var/lib/apt/lists/*

# Ubuntu 23.10 and above comes with an "ubuntu" user with uid 1000. We need 1000 for teku.
RUN userdel ubuntu 2>/dev/null || true && rm -rf /home/ubuntu  && \
    adduser --uid 1000 --disabled-password --gecos "" --home /opt/teku teku && \
    chown teku:teku /opt/teku && \
    chmod 0755 /opt/teku

USER teku
WORKDIR /opt/teku

# copy application (with libraries inside)
COPY --chown=teku:teku teku /opt/teku/

# Default to UTF-8 locale
ENV LANG C.UTF-8

ENV TEKU_REST_API_INTERFACE="0.0.0.0"
ENV TEKU_VALIDATOR_API_INTERFACE="0.0.0.0"
ENV TEKU_METRICS_INTERFACE="0.0.0.0"
ENV PATH "/opt/teku/bin:${PATH}"

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
      org.label-schema.description="Ethereum Consensus Client" \
      org.label-schema.url="https://docs.teku.consensys.io/" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/Consensys/teku.git" \
      org.label-schema.vendor="Consensys" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"
