#!/bin/sh



# "Usage: sh interop.sh [client] [interop_active] [validator_count] [owned_validator_start_index] [owned_validator_count] [peers]"
#
#
# Discv5
# Run multiclient in interop mode:
#   sh interop.sh multiclient 16 0 8
#
# Static Peering
# Run multiclient in interop mode:
#   sh interop.sh multiclient 16 0 16 /ip4/127.0.0.1/tcp/19001 10
#
# Run Lighthouse validator:
#   sh interop.sh lighthouse-validator 16 0 16


CLIENT=$1
export VALIDATOR_COUNT=$2
export OWNED_VALIDATOR_START_INDEX=$3
export OWNED_VALIDATOR_COUNT=$4
export PEERS=$5
START_DELAY=$6
export GENESIS_FILE=/tmp/genesis.ssz
export LIGHTHOUSE_DIR=$HOME/Projects/lighthouse/target/release

BOOTNODE_ENR=$(cat ~/.mothra/network/enr.dat)

CURRENT_TIME=$(date +%s)
GENESIS_TIME=$((CURRENT_TIME + START_DELAY))


zcli keys generate |zcli genesis mock --count $VALIDATOR_COUNT --genesis-time $GENESIS_TIME --out $GENESIS_FILE

if [ "$CLIENT" == "multiclient" ]
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
    sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" active true
    sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" genesisTime $GENESIS_TIME
    sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" ownedValidatorStartIndex $OWNED_VALIDATOR_START_INDEX
    sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" ownedValidatorCount $OWNED_VALIDATOR_COUNT

    if [ "$PEERS" != "" ]
    then
         ARTEMIS_PEERS=$(echo $PEERS | awk '{gsub(/\./,"\\.")}1' | awk '{gsub(/\//,"\\/")}1')
         ARTEMIS_PEERS=$(echo [\"$ARTEMIS_PEERS\"] )
         sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" peers $ARTEMIS_PEERS
         sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" discovery "\"static\""
         sh configurator.sh "$CONFIG_DIR/runConfig.0.toml" isBootnode false
    fi

    tmux new-session -d -s foo "cd $SCRIPT_DIR/demo/node_0/ && ./artemis --config=$CONFIG_DIR/runConfig.0.toml --logging=INFO"



    # Start lighthouse
    #TODO: make port configurable
    export LISTEN_ADDRESS=127.0.0.1
    export PORT=19001
    #TODO: use a relative path to lighthouse dir.  the best way would be to deploy it to $ARTEMIS_ROOT/scripts/demo/node_lighthouse

    # export RUST_LOG=libp2p_gossipsub=debug

    rm -rf ~/.lighthouse

    if [ "$PEERS" != "" ]
    then

        if [ "$GENESIS_FILE" != "" ]
        then
            tmux split-window -v -t 0 "cd $LIGHTHOUSE_DIR && ./beacon_node --libp2p-addresses $PEERS --listen-address $LISTEN_ADDRESS --port $PORT testnet -f file ssz $GENESIS_FILE"
        fi
    else
        #TODO: add tmux
        cd $DIR && ./beacon_node --boot-nodes $BOOTNODE_ENR --listen-address $LISTEN_ADDRESS --port $PORT testnet -r quick $VALIDATOR_COUNT $GENESIS_TIME
    fi



    # Start numbus






#elif [ "$CLIENT" == "lighthouse-validator" ]
#then
    #TODO: need to fix this so that it can either read in keys generated from zcli or
    #      Paul thinks that they will generate keys that match zcli
    export LIGHTHOUSE_OWNED_VALIDATOR_START_INDEX=$((OWNED_VALIDATOR_START_INDEX + OWNED_VALIDATOR_COUNT))
    echo $LIGHTHOUSE_OWNED_VALIDATOR_START_INDEX
    export LIGHTHOUSE_VALIDATOR_COUNT=$((VALIDATOR_COUNT - OWNED_VALIDATOR_COUNT))
    echo $LIGHTHOUSE_VALIDATOR_COUNT
    tmux split-window -h -t 0 "cd $LIGHTHOUSE_DIR  && ./validator_client testnet -b insecure $LIGHTHOUSE_OWNED_VALIDATOR_START_INDEX $LIGHTHOUSE_VALIDATOR_COUNT; sleep 20"
fi


tmux attach-session -d
