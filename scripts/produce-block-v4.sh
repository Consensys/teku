#!/usr/bin/env bash
#
# Produce a block via the Beacon API produceBlockV4 endpoint.
#   GET /eth/v4/validator/blocks/{slot}?randao_reveal=...
#
# Subscribes to the beacon node's SSE 'head' event stream, waits for the first
# head event, then requests a block for the next slot using a random
# randao_reveal and exits. skip_randao_verification is set so the node does not
# reject the random signature.
#
# Usage:
#   ./produce-block-v4.sh [BEACON_URL]
#
# Env:
#   BEACON_URL        Beacon node REST endpoint (default: http://localhost:5051)
#   INCLUDE_PAYLOAD   If set (expects 'true' or 'false'), passed as the
#                     include_payload query param on the produceBlockV4 request.
#   GRAFFITI          If set, passed as the graffiti query param. May be a plain
#                     UTF-8 string or a 0x-prefixed hex value; at most 32 bytes
#                     (bytes32).

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

# 4. Call produceBlockV4 and pretty-print the response. On a non-2xx status the
#    response body (which carries the error message) is printed so the failure
#    is visible.
extra_args=()
if [[ -n "${INCLUDE_PAYLOAD:-}" ]]; then
  extra_args+=(--data-urlencode "include_payload=${INCLUDE_PAYLOAD}")
  echo "include_payload: ${INCLUDE_PAYLOAD}" >&2
fi

if [[ -n "${GRAFFITI:-}" ]]; then
  # The API expects a bytes32 hex value. A 0x-prefixed value is used as-is;
  # a plain string is UTF-8 encoded to hex. Either way it is zero-padded on the
  # right to exactly 32 bytes.
  if [[ "$GRAFFITI" == 0x* || "$GRAFFITI" == 0X* ]]; then
    graffiti_hex="${GRAFFITI:2}"
    if [[ ! "$graffiti_hex" =~ ^[0-9a-fA-F]*$ ]]; then
      echo "error: GRAFFITI is 0x-prefixed but contains non-hex characters" >&2
      exit 1
    fi
    if (( ${#graffiti_hex} % 2 != 0 )); then
      echo "error: GRAFFITI hex value must have an even number of digits" >&2
      exit 1
    fi
  else
    graffiti_hex="$(printf '%s' "$GRAFFITI" | xxd -p -c 256 | tr -d '\n')"
  fi
  if (( ${#graffiti_hex} > 64 )); then
    echo "error: GRAFFITI is $(( ${#graffiti_hex} / 2 )) bytes; maximum is 32 (bytes32)" >&2
    exit 1
  fi
  # Right-pad with zeros to 32 bytes (64 hex chars).
  pad=$(( 64 - ${#graffiti_hex} ))
  if (( pad > 0 )); then
    printf -v zeros '%*s' "$pad" ''
    graffiti_hex="${graffiti_hex}${zeros// /0}"
  fi
  graffiti_value="0x${graffiti_hex}"
  extra_args+=(--data-urlencode "graffiti=${graffiti_value}")
  echo "graffiti:       ${GRAFFITI} -> ${graffiti_value}" >&2
fi

response="$(curl -sS -G "${BEACON_URL}/eth/v4/validator/blocks/${next_slot}" \
  --data-urlencode "randao_reveal=${randao_reveal}" \
  --data-urlencode "skip_randao_verification=" \
  "${extra_args[@]}" \
  -w $'\n%{http_code}')"

http_code="${response##*$'\n'}"
body="${response%$'\n'*}"

if [[ "$http_code" != 2* ]]; then
  echo "error: produceBlockV4 returned HTTP ${http_code}" >&2
  printf '%s\n' "$body" | jq . 2>/dev/null || printf '%s\n' "$body" >&2
  exit 1
fi

printf '%s\n' "$body" | jq .
