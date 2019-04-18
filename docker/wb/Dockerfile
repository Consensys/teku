FROM ubuntu:18.04

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y build-essential maven libsodium-dev \
    tmux wget iperf3 curl apt-utils iputils-ping expect npm git git-extras \
    software-properties-common openssh-server

# install java
RUN add-apt-repository ppa:openjdk-r/ppa && \
    apt-get update && \
    apt-get install -y openjdk-11-jdk && \
    rm -rf /var/lib/apt/lists/* 

# get artemis
RUN git clone --recursive https://github.com/PegaSysEng/artemis.git
WORKDIR artemis/
RUN ./gradlew build -x test
WORKDIR /artemis/build/distributions
RUN tar -xzf artemis-1.0.0-SNAPSHOT.tar.gz
RUN ln -s /artemis/build/distributions/artemis-*-SNAPSHOT/bin/artemis /usr/bin/artemis
WORKDIR /usr/local/bin
RUN wget https://github.com/Whiteblock/artemis_log_EATER/releases/download/v1.5.10/artemis-log-parser && chmod +x /usr/local/bin/artemis-log-parser

WORKDIR /
#ENV PATH="/artemis/build/distributions/artemis-1.0.0-SNAPSHOT/bin:${PATH}"

ENTRYPOINT ["/bin/bash"]