#!/bin/bash
set -euo pipefail

START=${1:?Must specify a start slot to download initial state from}
END=${2:?Must specify an end slot to stop downloading blocks at}
OUT=${3:-block-${START}-${END}}
PORT=${4:-5051}

mkdir -p "${OUT}"
OUT="$(cd "${OUT}" &>/dev/null && pwd)"

FIRST_BLOCK=$(($START + 1))
echo "Starting state download"
curl --fail -H 'Accept: application/octet-stream' http://localhost:$PORT/eth/v2/debug/beacon/states/{$START} -o "${OUT}/state.ssz" &

BLOCK_ARGS=""
for i in $(seq -f "%0.f" ${FIRST_BLOCK} ${END})
do
  echo "Fetching slot $i"
  set +e
  curl -s --fail -H 'Accept: application/octet-stream' http://localhost:$PORT/eth/v2/beacon/blocks/$i -o "${OUT}/$i.ssz" && BLOCK_ARGS="$BLOCK_ARGS $OUT/$i.ssz"
  set -e
done

echo "Waiting for state download..."

wait

echo "teku transition blocks --pre ${OUT}/state.ssz --post ${OUT}/post.ssz $BLOCK_ARGS"
