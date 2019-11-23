#!/bin/sh
#set -e
export TEST_DURATION=60
echo "Run interop_test for ${TEST_DURATION} seconds"
cd /go/go-ethereum/p2p/discover && go test -v -run TestNodesServer
