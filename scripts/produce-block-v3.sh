#!/usr/bin/env bash
#
# Produce a block via the Beacon API produceBlockV3 endpoint (pre-Gloas forks).
#   GET /eth/v3/validator/blocks/{slot}?randao_reveal=...
#
# Subscribes to the beacon node's SSE 'head' event stream, waits for the first
# head event, then requests a block for the next slot using a random
# randao_reveal and exits. skip_randao_verification is set so the node does not
# reject the random signature.
#
# Usage:
#   ./produce-block-v3.sh [BEACON_URL]
#
# Env:
#   BEACON_URL   Beacon node REST endpoint (default: http://localhost:5051)

set -euo pipefail

BEACON_URL="${1:-${BEACON_URL:-http://localhost:5051}}"

for cmd in curl jq; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "error: '$cmd' is required" >&2; exit 1; }
done

# 1. Subscribe to the head SSE event stream and read the first head event's slot.
echo "waiting for head event from ${BEACON_URL}..." >&2
head_slot=""
while IFS= read -r line; do
  # SSE data lines look like: data: {"slot":"123","block":"0x..",...}
  if [[ "$line" == data:* ]]; then
    head_slot="$(printf '%s' "${line#data:}" | jq -r '.slot // empty' 2>/dev/null || true)"
    if [[ -n "$head_slot" && "$head_slot" != "null" ]]; then
      break
    fi
  fi
done < <(curl -sfN -H 'Accept: text/event-stream' \
  "${BEACON_URL}/eth/v1/events?topics=head")

if [[ -z "$head_slot" || "$head_slot" == "null" ]]; then
  echo "error: could not read head slot from ${BEACON_URL} event stream" >&2
  exit 1
fi

# 2. Target the next slot.
next_slot=$((head_slot + 1))

# 3. Random randao_reveal: a 96-byte BLS signature (0x + 192 hex chars).
randao_reveal="0x$(head -c 96 /dev/urandom | xxd -p -c 256)"

echo "head slot:      ${head_slot}" >&2
echo "producing slot: ${next_slot}" >&2
echo "randao_reveal:  ${randao_reveal}" >&2
echo >&2

# 4. Call produceBlockV3 and pretty-print the response.
#    The fork (version) is returned in the Eth-Consensus-Version response header
#    and in the JSON body's "version" field. On a non-2xx status the response
#    body (which carries the error message) is printed so the failure is visible.
response="$(curl -sS -G "${BEACON_URL}/eth/v3/validator/blocks/${next_slot}" \
  --data-urlencode "randao_reveal=${randao_reveal}" \
  --data-urlencode "skip_randao_verification=" \
  -w $'\n%{http_code}')"

http_code="${response##*$'\n'}"
body="${response%$'\n'*}"

if [[ "$http_code" != 2* ]]; then
  echo "error: produceBlockV3 returned HTTP ${http_code}" >&2
  printf '%s\n' "$body" | jq . 2>/dev/null || printf '%s\n' "$body" >&2
  exit 1
fi

printf '%s\n' "$body" | jq .
