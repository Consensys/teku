#!/bin/bash
set -euo pipefail

# Instructs the beacon node to subscribe to all sync committee subnets.
# Then each slot monitors the number of peers available on those subnets.
# Useful for simulating a validator in a sync committee without needing a real validator.


URL="${1:-http://localhost:5051}"
METRICS_URL="${2:-http://localhost:8008/metrics}"
CURL="curl --fail -s"
GENESIS_TIME=$($CURL "${URL}/eth/v1/beacon/genesis" | jq -r .data.genesis_time)
CONFIG=$($CURL "${URL}/eth/v1/config/spec")
SECONDS_PER_SLOT=$(echo "${CONFIG}" | jq -r .data.SECONDS_PER_SLOT)
SLOTS_PER_EPOCH=$(echo "${CONFIG}"| jq -r .data.SLOTS_PER_EPOCH)

cat <<END
Configuration Detected
GENESIS_TIME: ${GENESIS_TIME}
SECONDS_PER_SLOT: ${SECONDS_PER_SLOT}
SLOTS_PER_EPOCH: ${SLOTS_PER_EPOCH}

END

while :
do
  NOW=$(date "+%s")
  SLOT=$(((${NOW} - ${GENESIS_TIME}) / ${SECONDS_PER_SLOT}))
  EPOCH=$((${SLOT} / ${SLOTS_PER_EPOCH}))
  # shellcheck disable=SC2006
  DATA=`cat<<END
[
  {
    "validator_index": "0",
    "sync_committee_indices": [ "0", "129", "258", "386" ],
    "until_epoch": "$((${EPOCH} + 2))"
  }
]
END`
  $CURL -X POST -H 'Content-Type: application/json' --data "${DATA}" "${URL}/eth/v1/validator/sync_committee_subscriptions" || echo "Failed to subscribe"
  PEER_COUNT=$($CURL "${URL}/eth/v1/node/peers" | jq .meta.count || echo 'Unknown')
  SUBNET_PEER_METRICS=$($CURL "${METRICS_URL}" | grep -v '#' | grep 'network_subnet_peer_count')
  SUBNET_PEER_COUNT_0=$(echo "${SUBNET_PEER_METRICS}" | grep 'sync_committee_0",}' | cut -f 2 -d ' ' || echo 'Unknown')
  SUBNET_PEER_COUNT_1=$(echo "${SUBNET_PEER_METRICS}" | grep 'sync_committee_1",}' | cut -f 2 -d ' ' || echo 'Unknown')
  SUBNET_PEER_COUNT_2=$(echo "${SUBNET_PEER_METRICS}" | grep 'sync_committee_2",}' | cut -f 2 -d ' ' || echo 'Unknown')
  SUBNET_PEER_COUNT_3=$(echo "${SUBNET_PEER_METRICS}" | grep 'sync_committee_3",}' | cut -f 2 -d ' ' || echo 'Unknown')
  echo "Slot: ${SLOT} Total peers: ${PEER_COUNT} Peers on sync subnets: ${SUBNET_PEER_COUNT_0} ${SUBNET_PEER_COUNT_1} ${SUBNET_PEER_COUNT_2} ${SUBNET_PEER_COUNT_3}"
  sleep "${SECONDS_PER_SLOT}"
done