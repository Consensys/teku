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
GEN_STATE=""
VALIDATOR_KEYS=""
PORT="8000"
usage() {
    echo "--identity=<identity>"
    echo "--peers=<peer>"
    echo "--gen-state=<file path>"
    echo port "--port=<port number>"
}
while [ "${1:-}" != "" ];
do
    PARAM=`echo ${1:-} | awk -F= '{print $1}'`
    VALUE=`echo ${1:-} | sed 's/^[^=]*=//g'`
    case $PARAM in
        --identity)
            IDENTITY=$VALUE
            ;;
        --peers)
            PEERS+="\"$VALUE\", "
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
bash "${DIR}/configurator.sh" "${CONFIG}" identity "\"${IDENTITY}\""
bash "${DIR}/configurator.sh" "${CONFIG}" networkMode "\"mothra\""
bash "${DIR}/configurator.sh" "${CONFIG}" peers "[${PEERS}]"
bash "${DIR}/configurator.sh" "${CONFIG}" startState "\"${GEN_STATE}\""
bash "${DIR}/configurator.sh" "${CONFIG}" port "${PORT}"
bash "${DIR}/configurator.sh" "${CONFIG}" validatorKeysFile "\"${VALIDATOR_KEYS}\""

echo "Generated config at ${CONFIG}"
/opt/artemis/bin/artemis -c "${CONFIG}"