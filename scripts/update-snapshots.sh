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

require_env "SERVER_GOERLI_URL"
require_env "SERVER_MAINNET_URL"
require_env "SERVER_SEPOLIA_URL"
require_env "SERVER_GNOSIS_URL"

if [[ "$all_env_set" = false ]]; then
  echo "Not all required environment variables are set. Following environment variables should be set:"
  echo "SERVER_GOERLI_URL: ssh url to Goerli server with Teku like 'user@8.8.8.8'"
  echo "SERVER_MAINNET_URL: ssh url to Mainnet server with Teku like 'user@8.8.8.8'"
  echo "SERVER_SEPOLIA_URL: ssh url to Sepolia server with Teku like 'user@8.8.8.8'"
  echo "SERVER_GNOSIS_URL: ssh url to Gnosis server with Teku like 'user@8.8.8.8'"
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

echo $'\nStarting download of deposit tree snapshots and verification data...'
OUT=$(echo "$OUT" | sed 's:/*$::')

downloader() {
  echo "" >&2
  echo "Downloading $1 snapshot, header, state" >&2
  ssh -f -o ExitOnForwardFailure=yes -L "${LOCAL_REST_API_BINDING_PORT}":localhost:5051 "${2}" sleep 1
  curl -s --fail -H 'Accept: application/octet-stream' http://localhost:"${LOCAL_REST_API_BINDING_PORT}"/teku/v1/beacon/deposit_snapshot -o "${OUT}/${1}.ssz"
  local result=$(curl -s --fail -H 'Accept: application/json' http://localhost:"${LOCAL_REST_API_BINDING_PORT}"/eth/v1/beacon/headers/finalized | jq '.data')
  echo $result > "${OUT}/${1}_header.json"
  curl -s --fail -H 'Accept: application/octet-stream' http://localhost:"${LOCAL_REST_API_BINDING_PORT}"/eth/v1/debug/beacon/states/finalized -o "${OUT}/${1}_state.ssz"
  local block_root=$(echo $result | jq -r '.root')
  echo "$block_root"
}

goerli_root=$(downloader "goerli" "${SERVER_GOERLI_URL}")
echo "GOERLI latest finalized slot verification link: https://goerli.beaconcha.in/slot/$goerli_root"
sepolia_root=$(downloader "sepolia" "${SERVER_SEPOLIA_URL}")
echo "SEPOLIA latest finalized slot verification link: https://sepolia.beaconcha.in/slot/$sepolia_root"
gnosis_root=$(downloader "gnosis" "${SERVER_GNOSIS_URL}")
echo "GNOSIS latest finalized slot verification link: https://beacon.gnosischain.com/block/$gnosis_root"
mainnet_root=$(downloader "mainnet" "${SERVER_MAINNET_URL}")
echo "MAINNET latest finalized slot verification link: https://beaconcha.in/slot/$mainnet_root"

echo $'\nAll done! Run verification tests and commit changes manually'
