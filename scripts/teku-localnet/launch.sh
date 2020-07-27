#!/bin/bash
set -euo pipefail


START_DELAY=20
CURRENT_TIME=$(date +%s)
export GENESIS_TIME=$((CURRENT_TIME + START_DELAY))

USER=${UID:-root} docker-compose --compatibility up