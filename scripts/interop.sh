#!/bin/sh



# "Usage: sh interop.sh [client] [interop_active] [validator_count] [owned_validator_start_index] [owned_validator_count] [peers]"
#
#
# Discv5
# Run multiclient in interop mode:
#   sh interop.sh 16 0 8
#
# Static Peering
# Run multiclient in interop mode:
#   sh interop.sh 16 0 0 /ip4/127.0.0.1/tcp/19001 10
#

export VALIDATOR_COUNT=$1
export OWNED_VALIDATOR_START_INDEX=$2
export OWNED_VALIDATOR_COUNT=$3
export PEERS=$4
START_DELAY=$5
export GENESIS_FILE="/tmp/genesis.ssz"


BOOTNODE_ENR=$(cat ~/.mothra/network/enr.dat)

CURRENT_TIME=$(date +%s)
GENESIS_TIME=$((CURRENT_TIME + START_DELAY))

export START_ARTEMIS=true
export START_LIGHTHOUSE=true
export START_TRINITY=false
export START_NIMBUS=true
export START_LODESTAR=true


zcli keys generate |zcli genesis mock --count $VALIDATOR_COUNT --genesis-time $GENESIS_TIME --out $GENESIS_FILE

if [ "$START_ARTEMIS" = true ]
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
fi


# Start lighthouse
if [ "$START_LIGHTHOUSE" = true ]
then
    #TODO: make port configurable
    export LISTEN_ADDRESS=127.0.0.1
    export PORT=19001
    #TODO: use a relative path to lighthouse dir.  the best way would be to deploy it to $ARTEMIS_ROOT/scripts/demo/node_lighthouse
	export DIR=$HOME/projects/consensys/pegasys/lighthouse/lighthouse/target/release

    # export RUST_LOG=libp2p_gossipsub=debug

    rm -rf ~/.lighthouse

    if [ "$PEERS" != "" ]
    then

        if [ "$GENESIS_FILE" != "" ]
        then
            tmux split-window -v -t 0 "cd $DIR && ./beacon_node --libp2p-addresses $PEERS --listen-address $LISTEN_ADDRESS --port $PORT testnet -f file ssz $GENESIS_FILE; sleep 20"
        fi
    else
        #TODO: add tmux
        cd $DIR && ./beacon_node --boot-nodes $BOOTNODE_ENR --listen-address $LISTEN_ADDRESS --port $PORT testnet -r quick $VALIDATOR_COUNT $GENESIS_TIME
    fi

    ######LIGHTHOUSE VALIDATOR
    #TODO: need to fix this so that it can either read in keys generated from zcli or
    #      Paul thinks that they will generate keys that match zcli
    export LIGHTHOUSE_OWNED_VALIDATOR_START_INDEX=$((OWNED_VALIDATOR_START_INDEX + OWNED_VALIDATOR_COUNT))
    echo $LIGHTHOUSE_OWNED_VALIDATOR_START_INDEX
    export LIGHTHOUSE_VALIDATOR_COUNT=$((VALIDATOR_COUNT - OWNED_VALIDATOR_COUNT))
    echo $LIGHTHOUSE_VALIDATOR_COUNT
    export DIR=$HOME/projects/consensys/pegasys/lighthouse/lighthouse/target/release
    if [ "$LIGHTHOUSE_VALIDATOR_COUNT" -gt  0 ]
    then
        tmux split-window -h -t 0 "cd $DIR  && ./validator_client testnet -b insecure $LIGHTHOUSE_OWNED_VALIDATOR_START_INDEX $LIGHTHOUSE_VALIDATOR_COUNT; sleep 20"
    fi
fi

if [ "$START_TRINITY" = true ]
then
    export PORT=19002
    # Start Trinity
    # export DIR=$HOME/projects/consensys/pegasys/trinity/
    export DIR=$HOME/.local/share/virtualenvs/trinity-Kj8PVxIn
    tmux split-window -h -t 0 "cd $DIR; PYTHONWARNINGS=ignore::DeprecationWarning trinity-beacon -l DEBUG --trinity-root-dir /tmp/bb --preferred_nodes= $PEERS interop --start-time $GENESIS_TIME --wipedb"

fi

# Start numbus
if [ "$START_NIMBUS" = true ]
then

    export PORT=19003
    export DIR=$HOME/projects/consensys/pegasys/nim-beacon-chain/multinet
    rm -f $DIR/validators/*
    rm -rf $DIR/data/node-0/db
    rm -f $DIR/data/state_snapshot.*
    tmux split-window -v -t 0 "cd $DIR;source ../env.sh; data/beacon_node --dataDir=data/node-0 --network=data/network.json --nodename=0 --tcpPort=$PORT --udpPort=$PORT --quickStart=true --stateSnapshot=$GENESIS_FILE"

fi

# Start Lodestar
if [ "$START_LODESTAR" = true ]
then
    export PORT=19004
    export DIR=$HOME/projects/consensys/pegasys/lodestar
    export LODESTAR_VALIDATOR_END_INDEX=0

    tmux split-window -h -t 0 "cd $DIR; packages/lodestar/./bin/lodestar interop -p minimal --db l1 -q $GENESIS_FILE -v $VALIDATOR_COUNT -r; sleep 20"
    #--multiaddrs /ip4/127.0.0.1/tcp/30607
fi

tmux select-layout tiled
tmux attach-session -d
