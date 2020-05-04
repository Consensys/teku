#!/usr/bin/env bash

# "Usage: sh interop.sh [validator_count] [owned_validator_start_index] [owned_validator_count] [start_delay]"
#
#
#
# Static Peering
# Run multiclient in interop mode:
#   sh interop.sh 16 0 16 10
#

usage() {
  echo "Runs a multiclient testnet"
  echo "Usage: sh interop.sh [validator_count] [owned_validator_start_index] [owned_validator_count] [start_delay]"
  echo "Example: Run multiple clients in interop mode using static peering. 16 validators and all are assigned to Teku"
  echo "         sh interop.sh 16 0 16 10"
}

if [ "$#" -ne 4 ]
then
  usage >&2;
  exit 1;
fi

export VALIDATOR_COUNT=$1
export OWNED_VALIDATOR_START_INDEX=$2
export OWNED_VALIDATOR_COUNT=$3
#export PEERS=$4
START_DELAY=$4
export GENESIS_FILE="/tmp/genesis.ssz"



CURRENT_TIME=$(date +%s)
GENESIS_TIME=$((CURRENT_TIME + START_DELAY))

export START_TEKU=true
export START_LIGHTHOUSE=true
export START_TRINITY=false
export START_NIMBUS=false
export START_LODESTAR=false
export START_PRYSM=false
export START_HARMONY=true

zcli keys generate |zcli genesis mock --count $VALIDATOR_COUNT --genesis-time $GENESIS_TIME --out $GENESIS_FILE

if [ "$START_TEKU" = true ]
then

    export TEKU_JAVA_VERSION=openjdk64-11.0.1
    SCRIPT_DIR=`pwd`
    CONFIG_DIR=$SCRIPT_DIR/../config

    source $SCRIPT_DIR/run_utils.sh

    rm -rf ./demo
    mkdir -p ./demo
    rm -f ../config/runConfig.*

    NODE_INDEX=0
    NUM_NODES=1

    configure_node "jvmlibp2p" $NODE_INDEX $NUM_NODES "$CONFIG_DIR/config.yaml"

    export PEER_ID=$(sed "$(($NODE_INDEX + 2))q;d" ../config/peer_ids.dat | cut -f 3)

    sh configurator.sh "$CONFIG_DIR/runConfig.0.yaml" numValidators $VALIDATOR_COUNT
    sh configurator.sh "$CONFIG_DIR/runConfig.0.yaml" numNodes $NUM_NODES
    sh configurator.sh "$CONFIG_DIR/runConfig.0.yaml" active true
    sh configurator.sh "$CONFIG_DIR/runConfig.0.yaml" genesisTime $GENESIS_TIME
    sh configurator.sh "$CONFIG_DIR/runConfig.0.yaml" startState "\"$GENESIS_FILE\""
    sh configurator.sh "$CONFIG_DIR/runConfig.0.yaml" ownedValidatorStartIndex $OWNED_VALIDATOR_START_INDEX
    sh configurator.sh "$CONFIG_DIR/runConfig.0.yaml" ownedValidatorCount $OWNED_VALIDATOR_COUNT


    #if [ "$PEERS" != "" ]
    #then
    #     TEKU_PEERS=$(echo $PEERS | awk '{gsub(/\./,"\\.")}1' | awk '{gsub(/\//,"\\/")}1')
    #     TEKU_PEERS=$(echo [\"$TEKU_PEERS\"] )
    #     sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" peers $TEKU_PEERS
         sh configurator.sh "$CONFIG_DIR/runConfig.0.yaml" discovery "\"static\""
         sh configurator.sh "$CONFIG_DIR/runConfig.0.yaml" isBootnode false
    #fi
    sed -i "" '10d' "$CONFIG_DIR/runConfig.0.yaml"
    tmux new-session -d -s foo "jenv local $TEKU_JAVA_VERSION; cd $SCRIPT_DIR/demo/node_0/ && ./teku --config=$CONFIG_DIR/runConfig.0.yaml --logging=DEBUG; sleep 20"
fi


# Start lighthouse
if [ "$START_LIGHTHOUSE" = true ]
then

    #TODO: make port configurable
    export LISTEN_ADDRESS=127.0.0.1
    export PORT=19001
    #TODO: use a relative path to lighthouse dir.  the best way would be to deploy it to $TEKU_ROOT/scripts/demo/node_lighthouse
    export DIR=$HOME/projects/consensys/pegasys/lighthouse/lighthouse/target/release

    export RUST_LOG=libp2p_gossipsub=debug

    rm -rf ~/.lighthouse

    tmux split-window -v -t 0 "sleep 5; cd $DIR && ./beacon_node --libp2p-addresses /ip4/127.0.0.1/tcp/19000  --listen-address $LISTEN_ADDRESS --port $PORT testnet -f file ssz $GENESIS_FILE; sleep 20"


    ######LIGHTHOUSE VALIDATOR
    #TODO: need to fix this so that it can either read in keys generated from zcli or
    #      Paul thinks that they will generate keys that match zcli
    export LIGHTHOUSE_OWNED_VALIDATOR_START_INDEX=$((OWNED_VALIDATOR_START_INDEX + OWNED_VALIDATOR_COUNT))
    export LIGHTHOUSE_VALIDATOR_COUNT=$((VALIDATOR_COUNT - OWNED_VALIDATOR_COUNT))
    export DIR=$HOME/projects/consensys/pegasys/lighthouse/lighthouse/target/release


   #### need to rework the way validators are distributed
   # if [ "$LIGHTHOUSE_VALIDATOR_COUNT" -gt  0 ]
   # then
   #     tmux split-window -h -t 0 "cd $DIR  && ./validator_client testnet -b insecure $LIGHTHOUSE_OWNED_VALIDATOR_START_INDEX $LIGHTHOUSE_VALIDATOR_COUNT; sleep 20"
   # fi
fi

if [ "$START_TRINITY" = true ]
then

## The Pipfile and the run_trinity.sh script need to live in te root trinity directory

## Pipfile
#[[source]]
#name = "pypi"
#url = "https://pypi.org/simple"
#verify_ssl = true
#
#[dev-packages]
#
#[packages]
#
#[requires]
#python_version = "3.7"
#
#[scripts]
#serve = "./run_trinity.sh"

## run_trinity.sh
#DIR=$1
#GENESIS_TIME=$2
#GENESIS_FILE=$3
#PORT=$4

#cd $DIR
#PYTHONWARNINGS=ignore::DeprecationWarning trinity-beacon -l DEBUG --trinity-root-dir /tmp/bb --beacon-nodekey='aaaaaaaaaa' --port $PORT interop --start-time $GENESIS_TIME --wipedb --genesis $GENESIS_FILE

    export PORT=19002
    # Start Trinity
    export DIR=$HOME/projects/consensys/pegasys/trinity/
    tmux split-window -h -t 0 "sleep 5; cd $DIR; pipenv run serve $DIR $GENESIS_TIME $GENESIS_FILE $PORT; sleep 20"

fi

# Start numbus
if [ "$START_NIMBUS" = true ]
then

    export PORT=19003
    export DIR=$HOME/projects/consensys/pegasys/nim-beacon-chain/multinet
    rm -f $DIR/validators/*
    rm -rf $DIR/data/node-0/db
    rm -f $DIR/data/state_snapshot.*
    tmux split-window -v -t 0 "sleep 5; cd $DIR;source ../env.sh; data/beacon_node --dataDir=data/node-0 --network=data/network.json --nodename=0 --tcpPort=$PORT --udpPort=$PORT --quickStart=true --stateSnapshot=$GENESIS_FILE"

fi

# Start Lodestar
if [ "$START_LODESTAR" = true ]
then

    #node_modules/.bin/lerna bootstrap

    export PORT=19004
    export DIR=$HOME/projects/consensys/pegasys/lodestar
    export LODESTAR_VALIDATOR_END_INDEX=0

    tmux split-window -h -t 0 "sleep 5; cd $DIR; packages/lodestar/./bin/lodestar interop -p minimal --db l1 -q $GENESIS_FILE -v 0 --multiaddrs /ip4/127.0.0.1/tcp/19004  --bootnodes /ip4/127.0.0.1/tcp/19000/p2p/$PEER_ID -v 16 -r; sleep 20"
    #--multiaddrs /ip4/127.0.0.1/tcp/30607

fi

if [ "$START_PRYSM" = true ]
then
    export PORT=19005
    export DIR=$HOME/projects/consensys/pegasys/prysm/darwin_amd64
    tmux split-window -h -t 0 "sleep 5; cd $DIR; ./beacon-chain --datadir /tmp/beacon \
   --pprof --verbosity=debug \
   --clear-db \
   --bootstrap-node= \
   --interop-eth1data-votes \
   --peer /ip4/127.0.0.1/tcp/19000/p2p/$PEER_ID \
   --deposit-contract=0xD775140349E6A5D12524C6ccc3d6A1d4519D4029 \
   --interop-genesis-state $GENESIS_FILE \
   --p2p-port $PORT; sleep 60"


fi

if [ "$START_HARMONY" = true ]
then
    export HARMONY_OWNED_VALIDATOR_START_INDEX=0
    export HARMONY_VALIDATOR_END_INDEX=$((OWNED_VALIDATOR_COUNT-1))
    echo start_index $HARMONY_OWNED_VALIDATOR_START_INDEX
    echo end_index $HARMONY_VALIDATOR_END_INDEX
    export HARMONY_JAVA_VERSION=1.8.0.192
    export PORT=19006
    export DIR=$HOME/projects/consensys/pegasys/beacon-chain-java/node-0.2.0/bin
    #tmux split-window -h -t 0 "echo start_index $HARMONY_OWNED_VALIDATOR_START_INDEX;  echo end_index $HARMONY_VALIDATOR_END_INDEX; cd $DIR; jenv local $HARMONY_JAVA_VERSION; ./node default --connect=/ip4/127.0.0.1/tcp/19000/p2p/16Uiu2HAmLyZqiwTqVPEnYYFDRbpYFsaWdxTgPiRRJuzDx8A1Dj1q --listen=$PORT --force-db-clean --spec-constants minimal --initial-state=$GENESIS_FILE --validators=$HARMONY_OWNED_VALIDATOR_START_INDEX-$HARMONY_VALIDATOR_END_INDEX; sleep 20"
    tmux split-window -h -t 0 "cd $DIR; jenv local $HARMONY_JAVA_VERSION; ./node default --connect=/ip4/127.0.0.1/tcp/19000/p2p/$PEER_ID --listen=$PORT --force-db-clean --spec-constants minimal --initial-state=$GENESIS_FILE; sleep 20"
fi



tmux select-layout tiled
tmux attach-session -d
