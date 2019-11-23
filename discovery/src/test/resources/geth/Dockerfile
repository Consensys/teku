# Build Geth branch with discv5 implementation
FROM golang:1.12-alpine as builder

RUN apk add --no-cache make gcc musl-dev linux-headers git

ARG branch=discover-v5-rebase
RUN git clone --depth 1 --branch $branch https://github.com/fjl/go-ethereum.git

ADD . /go-ethereum
RUN cd /go/go-ethereum && go mod init github.com/ethereum/go-ethereum

ADD v5_interop_test.go /go/go-ethereum/p2p/discover/v5_interop_test.go
ADD test.sh /test.sh
RUN chmod +x /test.sh

# Idle run to download all dependencies for the test
ENV TEST_DURATION=1
RUN cd /go/go-ethereum/p2p/discover && go test -run TestNodesServer

EXPOSE 8545 8546 30303 30303/udp
ENTRYPOINT ["/test.sh"]