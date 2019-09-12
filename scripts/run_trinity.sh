

#START_DELAY=$1
#VALIDATOR_COUNT=$2
DIR=$1
GENESIS_TIME=$2
GENESIS_FILE=$3
PORT=$4
#CURRENT_TIME=$(date +%s)
#GENESIS_TIME=$((CURRENT_TIME + START_DELAY))
#GENESIS_FILE="/tmp/genesis.ssz"
#echo $GENESIS_TIME
#zcli keys generate |zcli genesis mock --count $VALIDATOR_COUNT --genesis-time $GENESIS_TIME --out /tmp/genesis.ssz


cd $DIR
PYTHONWARNINGS=ignore::DeprecationWarning trinity-beacon -l DEBUG --trinity-root-dir /tmp/bb --beacon-nodekey='aaaaaaaaaa' --port $PORT interop --start-time $GENESIS_TIME --wipedb --genesis $GENESIS_FILE
