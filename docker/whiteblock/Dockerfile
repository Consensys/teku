FROM ubuntu:latest
	
RUN apt-get update &&\ 
	DEBIAN_FRONTEND=noninteractive apt-get install -y build-essential maven libsodium-dev tmux \
	apt-utils iputils-ping expect npm git git-extras software-properties-common golang-go

# install java
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
	add-apt-repository -y ppa:webupd8team/java && \
	apt-get update && \
	apt-get install -y oracle-java8-installer && \
	rm -rf /var/lib/apt/lists/* && \
	rm -rf /var/cache/oracle-jdk8-installer
ENV JAVA_HOME="/usr/lib/jvm/java-8-oracle"

# get artemis
RUN git clone --recursive https://github.com/PegaSysEng/artemis.git 
WORKDIR artemis/
RUN ./gradlew --no-daemon

ENTRYPOINT ["/bin/bash"]
