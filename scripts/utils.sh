#!/bin/sh

clean() {
  local DIR=$1
  rm -rf $DIR
  mkdir -p $DIR
}

create_config() {
  local NODE="$1"
  local TOTAL="$2"
  local PEERS=$(echo "$PEERS" | sed "s/\"hob+tcp:\/\/abcf@localhost:$((19000 + $NODE))\"//g")
  PEERS="[$(echo $PEERS | tr ' ' ',')]"
  local PORT=$((19000 + $NODE))
  local IDENTITY
  # Make sure that the identity is one byte TODO
  if [[ "$NODE" -lt 16 ]]
  then
    printf -v IDENTITY "0x0%X" $NODE
  else
    printf -v IDENTITY "0x%X" $NODE
  fi
  # Create the configuration file for the node
  cat ../config/config.toml | \
    sed "s/advertisedPort\ =.*//" | \
    sed "s/LATEST_BLOCK_ROOTS_LENGTH\ =.*//" | \
    sed "s/DOMAIN_PROPOSAL\ =.*//" | \
    sed "s/DOMAIN_EXIT\ =.*//" | \
    sed "s/identity\ =.*/identity\ =\ \"$IDENTITY\"/" | \
    sed "s/port\ =.*/port\ =\ $PORT/" | \
    awk -v peers="$PEERS" '/port/{print;print "peers = "peers;next}1' | \
    sed "s/numNodes\ =.*/numNodes\ =\ $TOTAL/" | \
    sed "s/networkInterface\ =.*/networkInterface\ =\ \"127.0.0.1\"/" | \
    sed "s/networkMode\ =.*/networkMode\ =\ \"hobbits\"/" | \
    sed "s/DOMAIN_RANDAO\ =.*/DOMAIN_RANDAO\ =\ 1/" | \
    sed "s/DOMAIN_ATTESTATION\ =.*/DOMAIN_ATTESTATION\ =\ 2/" | \
    sed "s/DOMAIN_DEPOSIT\ =.*/DOMAIN_DEPOSIT\ =\ 3/" \
    > ../config/runConfig.$NODE.toml
  echo "DOMAIN_BEACON_BLOCK = 0" >> ../config/runConfig.$NODE.toml
  echo "DOMAIN_VOLUNTARY_EXIT = 4" >> ../config/runConfig.$NODE.toml
  echo "DOMAIN_TRANSFER = 5" >> ../config/runConfig.$NODE.toml
}

configure_node() {
  local NODE="$1"
  tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
  mv ./demo/artemis-* ./demo/node_$NODE
  ln -s ../../../config ./demo/node_$NODE/
  cd demo/node_$NODE && ln -s ./bin/artemis . && cd ../../
  create_config "$NODE" "$2" "$3"
}

usage() {
  echo "Usage: sh run.sh NODES"
  echo "Runs a simulation of artemis with NODES nodes, where NODES must be greater than zero"
}
