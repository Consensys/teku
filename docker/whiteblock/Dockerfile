FROM pegasyseng/artemis:develop

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y build-essential \
    tmux wget iperf3 curl apt-utils iputils-ping expect git git-extras \
    software-properties-common openssh-server

WORKDIR /usr/local/bin
RUN wget https://github.com/Whiteblock/artemis_log_EATER/releases/download/v1.5.10/artemis-log-parser && chmod +x /usr/local/bin/artemis-log-parser

COPY start.sh /opt/whiteblock/
COPY scripts /opt/whiteblock/scripts

WORKDIR /
ENV PATH="/opt/artemis/bin/:${PATH}"

ENTRYPOINT ["/bin/bash"]