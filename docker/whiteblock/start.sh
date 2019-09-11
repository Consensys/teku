#!/usr/bin/env bash
set -euo pipefail
DIR="/opt/whiteblock/scripts"

OUT=$(mktemp -d)

function cleanup() {
  rm -rf "${OUT}"
}
trap cleanup EXIT

IDENTITY=""
PEERS=""
NUM_VALIDATORS="3"
GEN_STATE=""
VALIDATOR_KEYS=""
PORT="8000"
usage() {
    echo "--identity=<identity>"
    echo "--peer=<peer>"
    echo "--num-validators=<number>"
    echo "--gen-state=<file path>"
    port "--port=<port number>"
}
while [ "${1:-}" != "" ];
do
    PARAM=`echo ${1:-} | awk -F= '{print $1}'`
    VALUE=`echo ${1:-} | sed 's/^[^=]*=//g'`
    case $PARAM in
        --identity)
            IDENTITY=$VALUE
            ;;
        --peer)
            PEERS+="\"$VALUE\", "
            ;;
        --num-validators)
            NUM_VALIDATORS=$VALUE
            ;;
        --gen-state)
            GEN_STATE=$VALUE
            ;;
        --port)
            PORT=$VALUE
            ;;
        --validator-keys)
            VALIDATOR_KEYS=${VALUE}
            ;;
        --help)
            usage
            exit
            ;;
        *)
            echo "ERROR: unknown parameter \"$PARAM\""
            usage
            exit 1
            ;;
    esac
    shift
done

CONFIG_DIR="${DIR}/../config"
CONFIG="${OUT}/generated_config.toml"
cp "$CONFIG_DIR/config.toml" "${CONFIG}"
bash "${DIR}/configurator.sh" "${CONFIG}" numValidators "${NUM_VALIDATORS}"
bash "${DIR}/configurator.sh" "${CONFIG}" identity "\"${IDENTITY}\""
bash "${DIR}/configurator.sh" "${CONFIG}" networkMode "\"mothra\""
bash "${DIR}/configurator.sh" "${CONFIG}" peers "[${PEERS}]"
bash "${DIR}/configurator.sh" "${CONFIG}" startState "\"${GEN_STATE}\""
bash "${DIR}/configurator.sh" "${CONFIG}" port "${PORT}"
bash "${DIR}/configurator.sh" "${CONFIG}" validatorKeysFile "\"${VALIDATOR_KEYS}\""

echo "Generated config at ${CONFIG}"
/opt/artemis/bin/artemis -c "${CONFIG}"