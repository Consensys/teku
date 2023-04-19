#!/bin/bash
set -euo pipefail

LOCAL_REST_API_BINDING_PORT=15051
SNAPSHOT_DOWNLOAD_SCRIPT="./scripts/fetch-deposit-snapshot.sh"

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

require_env "GOERLI_SSH"
require_env "MAINNET_SSH"
require_env "SEPOLIA_SSH"
require_env "GNOSIS_SSH"

if [[ "$all_env_set" = false ]]; then
  echo "Not all required environment variables are set. Following environment variables should be set:"
  echo "GOERLI_SSH: ssh user-host for Goerli server with Teku like 'user@8.8.8.8'"
  echo "MAINNET_SSH: ssh user-host for Mainnet server with Teku like 'user@8.8.8.8'"
  echo "SEPOLIA_SSH: ssh user-host for Sepolia server with Teku like 'user@8.8.8.8'"
  echo "GNOSIS_SSH: ssh user-host for Gnosis server with Teku like 'user@8.8.8.8'"
  exit 89
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

downloader "goerli" "${GOERLI_SSH}"
downloader "sepolia" "${SEPOLIA_SSH}"
downloader "gnosis" "${GNOSIS_SSH}"
downloader "mainnet" "${MAINNET_SSH}"

echo $'\nAll done! Run verification tests and commit changes manually'
