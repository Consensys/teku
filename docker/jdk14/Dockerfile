FROM openjdk:14-slim-buster

RUN adduser --disabled-password --gecos "" --home /opt/teku teku && \
    chown teku:teku /opt/teku

USER teku
WORKDIR /opt/teku

# copy application (with libraries inside)
COPY --chown=teku:teku teku /opt/teku/

ENV TEKU_REST_API_INTERFACE="0.0.0.0"
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
