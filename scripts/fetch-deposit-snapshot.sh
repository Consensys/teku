#!/bin/bash
set -euo pipefail

REST_API=${1:?Must specify Teku Rest API url}
VERIFY=${2:?Whether to check that snapshot points to higher deposit count than existing}
OUT=${3:?Out filename}

if [[ REST_API != *deposit_snapshot ]]
then
  REST_API=$(echo "$REST_API" | sed 's:/*$::')
  REST_API="${REST_API}/teku/v1/beacon/deposit_snapshot"
fi
temp_file=$(mktemp)
trap "rm -f $temp_file" 0 2 3 15
echo ${REST_API}
curl --fail -H 'Accept: application/json' "${REST_API}" -o "${temp_file}"

new_snapshot_deposit_count=$(jq -r '.data.deposit_count' ${temp_file})
new_snapshot_block_height=$(jq -r '.data.execution_block_height' ${temp_file})
echo "Downloaded deposit snapshot with deposit count: $new_snapshot_deposit_count, block height: $new_snapshot_block_height"
if [ -f "$OUT" ]; then
  old_snapshot_block_height=$(jq -r '.data.execution_block_height' ${OUT})
  echo "Found old deposit snapshot with $old_snapshot_block_height block height"
  if (( "$new_snapshot_block_height" > "$old_snapshot_block_height" )); then
      more_deposits=true
  else
      more_deposits=false
  fi
  if [[ "$VERIFY" = false || ("$VERIFY" = true  && "$more_deposits" = true) ]]; then
    echo "Replacing old file ${OUT} with new deposit snapshot"
    jq . "${temp_file}" > "$OUT"
  else
    echo "Deposit snapshot is up-to-date";fi
else
  echo "Writing deposit snapshot to ${OUT}"
  mv "${temp_file}" "$OUT"; fi
