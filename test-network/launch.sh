#!/bin/bash
set -euo pipefail
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

cd "${DIR}"

mkdir -p data/node1 data/node2 data/node3 data/node4

START_DELAY=30
CURRENT_TIME=$(date +%s)
export GENESIS_TIME=$((CURRENT_TIME + START_DELAY))
cp "${DIR}/../dashboard/teku-dashboard-grafana.json" "${DIR}/grafana/provisioning/dashboards/"

USER=${UID:-root} docker-compose --compatibility up