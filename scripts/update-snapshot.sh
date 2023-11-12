#!/bin/bash
set -euo pipefail

LOCAL_REST_API_BINDING_PORT=15051
valid_networks=("gnosis" "goerli" "lukso" "mainnet" "sepolia")
OUT=${1:?Must specify destination directory for snapshots}

echo $'\nChecking that required environment variables are set:'
all_env_set=true
require_env() {
  echo -n "Checking environment variable: $1... "
  env_name="$1"
  if [ -z ${!1+set} ]; then
    echo "FAILED"
    all_env_set=false
  else
    echo "OK"
  fi
}

require_env "DEPOSIT_NETWORK"
require_env "DEPOSIT_SSH"

if [[ "$all_env_set" = false ]]; then
  echo "Not all required environment variables are set. Following environment variables should be set:"
  echo "DEPOSIT_SSH: ssh host for server with Teku like '8.8.8.8'"
  echo "DEPOSIT_NETWORK: network name, one of gnosis, goerli, lukso, mainnet, sepolia"
  exit 1
fi

network=$DEPOSIT_NETWORK
if [[ " ${valid_networks[@]} " =~ " ${network} " ]]; then
  echo "DEPOSIT_NETWORK is set to a valid network: $network"
else
  echo "DEPOSIT_NETWORK is not set to a valid network. Exiting."
  echo "Supported networks are: ${valid_networks[@]}"
  exit 1
fi

echo $'\nChecking that required software available:'

require_command() {
  echo -n "Checking $1... "
  if which "${1}" &> /dev/null; then
    echo "OK"
  else
    echo "FAILED" && exit 65
  fi
}

require_command "curl"
require_command "jq"
require_command "sed"
require_command "ssh"

echo $'\nStarting download of deposit tree snapshots...'
OUT=$(echo "$OUT" | sed 's:/*$::')

downloader() {
  echo "Downloading $1 snapshot" >&2
  ssh -f -o ExitOnForwardFailure=yes -o ConnectTimeout=10 -L "${LOCAL_REST_API_BINDING_PORT}":localhost:5051 "${2}" sleep 1
  curl -s --show-error --fail -H 'Accept: application/octet-stream' http://localhost:"${LOCAL_REST_API_BINDING_PORT}"/eth/v1/beacon/deposit_snapshot -o "${OUT}/${1}.ssz" >&2
}

downloader "${DEPOSIT_NETWORK}" "${DEPOSIT_SSH}"

echo -e "\nAll done for ${DEPOSIT_NETWORK}!"
