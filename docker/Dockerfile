FROM openjdk:11.0.2-jre-slim-stretch

# copy application (with libraries inside)
COPY teku /opt/teku/

# List Exposed Ports
EXPOSE 8008 8084 8545 30303 30303/udp

# specify default command
ENTRYPOINT ["/opt/teku/bin/teku"]


# Build-time metadata as defined at http://label-schema.org
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="Teku" \
      org.label-schema.description="Ethereum 2.0 Beacon Chain Client" \
      org.label-schema.url="https://pegasys.tech/" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/PegaSysEng/artemis.git" \
      org.label-schema.vendor="Pegasys" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"