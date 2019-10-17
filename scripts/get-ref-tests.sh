#!/bin/bash

cd eth-reference-tests/src/referenceTest/resources/
rm -rf eth2.0-spec-tests && mkdir -p eth2.0-spec-tests/tests
curl -L -o general.tar.gz https://github.com/ethereum/eth2.0-spec-tests/releases/download/v0.8.3/general.tar.gz
curl -L -o mainnet.tar.gz https://github.com/ethereum/eth2.0-spec-tests/releases/download/v0.8.3/mainnet.tar.gz
curl -L -o minimal.tar.gz https://github.com/ethereum/eth2.0-spec-tests/releases/download/v0.8.3/minimal.tar.gz
tar -xzf general.tar.gz -C eth2.0-spec-tests/tests --strip-components 1
tar -xzf mainnet.tar.gz -C eth2.0-spec-tests/tests --strip-components 1
tar -xzf minimal.tar.gz -C eth2.0-spec-tests/tests --strip-components 1
rm -rf *.tar.gz