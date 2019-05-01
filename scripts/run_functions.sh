#!/bin/sh

function clean() {
  local DIR=$1
  rm -rf $DIR
  mkdir -p $DIR
}

function configure_node() {
  local NODE=$1 
  tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
  mv ./demo/artemis-* ./demo/node_$NODE
  ln -s ../../../config ./demo/node_$NODE/
  cd demo/node_$NODE && ln -s ./bin/artemis . && cd ../../
}
