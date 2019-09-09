#!/bin/sh



# "Usage: sh interop.sh [client] [interop_active] [validator_count] [owned_validator_start_index] [owned_validator_count] [peers]"
#
# Run Artemis (single node) in non-interop mode:
#   sh interop.sh artemis false 16
#
# Discv5
# Run Artemis in interop mode:
#   sh interop.sh artemis true 16 0 8
#
# Run Lighthouse node:
#   sh interop.sh lighthouse-node true 16
#
# Run Lighthouse validator:
#   sh interop.sh lighthouse-validator true 16 8 8
#
# Static Peering
# Run Artemis in interop mode:
#   sh interop.sh artemis true 16 0 0 /ip4/127.0.0.1/tcp/19001 30
#
# Run Lighthouse node:
#   sh interop.sh lighthouse-node true 16 0 16 /ip4/127.0.0.1/tcp/19000 30
#
# Run Lighthouse validator:
#   sh interop.sh lighthouse-validator true 16 0 16


CLIENT=$1
IS_INTEROP_ACTIVE=$2
export VALIDATOR_COUNT=$3
export OWNED_VALIDATOR_START_INDEX=$4
export OWNED_VALIDATOR_COUNT=$5
export PEERS=$6
START_DELAY=$7
export GENESIS_FILE=/tmp/genesis.ssz

BOOTNODE_ENR=$(cat ~/.mothra/network/enr.dat)

CURRENT_TIME=$(date +%s)
GENESIS_TIME=$((CURRENT_TIME + START_DELAY))


zcli keys generate |zcli genesis mock --count $VALIDATOR_COUNT --genesis-time $GENESIS_TIME --out $GENESIS_FILE

if [ "$CLIENT" == "artemis" ]
then

    SCRIPT_DIR=`pwd`
    CONFIG_DIR=$SCRIPT_DIR/../config

    source $SCRIPT_DIR/run_utils.sh

    rm -rf ./demo
    mkdir -p ./demo
    rm -f ../config/runConfig.*

    NODE_INDEX=0
    NUM_NODES=1

    configure_node "mothra" $NODE_INDEX $NUM_NODES "$CONFIG_DIR/config.toml"
    sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" numValidators $VALIDATOR_COUNT
    sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" numNodes $NUM_NODES

    if [ "$IS_INTEROP_ACTIVE" == "true" ]
    then
        sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" active true
        sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" genesisTime $GENESIS_TIME
        sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" ownedValidatorStartIndex $OWNED_VALIDATOR_START_INDEX
        sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" ownedValidatorCount $OWNED_VALIDATOR_COUNT
    fi

    if [ "$PEERS" != "" ]
    then
         ARTEMIS_PEERS=$(echo $PEERS | awk '{gsub(/\./,"\\.")}1' | awk '{gsub(/\//,"\\/")}1')
         ARTEMIS_PEERS=$(echo [\"$ARTEMIS_PEERS\"] )
         sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" peers $ARTEMIS_PEERS
         sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" discovery "\"static\""
         sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" isBootnode false
    fi

   tmux new-session -d -s foo "cd $SCRIPT_DIR/demo/node_0/ && ./artemis --config=$CONFIG_DIR/runConfig.0.toml --logging=INFO"

    export LISTEN_ADDRESS=127.0.0.1
    export PORT=19001
    export DIR=$HOME/projects/consensys/pegasys/lighthouse/lighthouse/target/release

    # Start lighthouse
    # export RUST_LOG=libp2p_gossipsub=debug

    rm -rf ~/.lighthouse

    if [ "$PEERS" != "" ]
    then

        if [ "$GENESIS_FILE" != "" ]
        then
            tmux split-window -v -t 0 "cd $DIR && ./beacon_node --libp2p-addresses $PEERS --listen-address $LISTEN_ADDRESS --port $PORT testnet -f file ssz $GENESIS_FILE"
        else
            cd $DIR && ./beacon_node --libp2p-addresses $PEERS --listen-address $LISTEN_ADDRESS --port $PORT testnet -r quick $VALIDATOR_COUNT $GENESIS_TIME
        fi
    else
        cd $DIR && ./beacon_node --boot-nodes $BOOTNODE_ENR --listen-address $LISTEN_ADDRESS --port $PORT testnet -r quick $VALIDATOR_COUNT $GENESIS_TIME
    fi
    tmux attach-session -d

elif [ "$CLIENT" == "lighthouse-validator" ]
then
    export DIR=$HOME/projects/consensys/pegasys/lighthouse/lighthouse/target/release
    tmux split-window -h -t 0 "cd $DIR  && ./validator_client testnet -b insecure $OWNED_VALIDATOR_START_INDEX $OWNED_VALIDATOR_COUNT"
fi
