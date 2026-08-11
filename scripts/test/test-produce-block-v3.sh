#!/usr/bin/env bash
#
# Basic test coverage for produce-block-v3.sh.
#
# Input-validation tests run unconditionally (they fail fast without a node).
# Block-production tests only run when a beacon node is reachable; node
# availability is probed via GET /eth/v1/node/identity.
#
# Usage:
#   ./test-produce-block-v3.sh [BEACON_URL]
#
# Env:
#   BEACON_URL   Beacon node REST endpoint (default: http://localhost:5051)

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCE="${SCRIPT_DIR}/../produce-block-v3.sh"
BEACON_URL="${1:-${BEACON_URL:-http://localhost:5051}}"

pass=0
fail=0

ok()   { echo "  PASS: $1"; pass=$((pass + 1)); }
bad()  { echo "  FAIL: $1"; fail=$((fail + 1)); }

# expect_reject <desc> <needle> <cmd...>
# Assert <cmd...> exits non-zero and prints an error containing <needle>.
expect_reject() {
  local desc="$1" needle="$2"; shift 2
  local out rc
  out="$("$@" "$PRODUCE" "$BEACON_URL" 2>&1)"
  rc=$?
  if (( rc == 0 )); then
    bad "${desc} (expected non-zero exit, got 0)"
  elif [[ "$out" != *"$needle"* ]]; then
    bad "${desc} (missing '${needle}' in output: ${out})"
  else
    ok "$desc"
  fi
}

# expect_graffiti_prefix <desc> <expected-hex-prefix> <cmd...>
# Run <cmd...>, extract the block graffiti, assert it starts with the prefix.
expect_graffiti_prefix() {
  local desc="$1" prefix="$2"; shift 2
  local graffiti
  graffiti="$("$@" "$PRODUCE" "$BEACON_URL" 2>/dev/null | jq -r '.data.body.graffiti')"
  if [[ "$graffiti" == "${prefix}"* ]]; then
    ok "${desc} -> ${graffiti}"
  else
    bad "${desc} (expected prefix ${prefix}, got ${graffiti})"
  fi
}

echo "== input validation (no node required) =="
# Oversized string (33 bytes > 32).
expect_reject "rejects >32-byte string" "maximum is 32" \
  env GRAFFITI="this is exactly thirty-three byte"
# Oversized hex (33 bytes).
expect_reject "rejects >32-byte hex" "maximum is 32" \
  env GRAFFITI="0x$(printf '%066x' 0)"
# Odd number of hex digits.
expect_reject "rejects odd-length hex" "even number of digits" \
  env GRAFFITI="0xabc"
# Non-hex characters after 0x.
expect_reject "rejects non-hex 0x value" "non-hex characters" \
  env GRAFFITI="0xzz"

echo
echo "== node probe (${BEACON_URL}/eth/v1/node/identity) =="
if ! curl -sf "${BEACON_URL}/eth/v1/node/identity" >/dev/null 2>&1; then
  echo "  SKIP: node not reachable; skipping block-production tests"
  echo
  echo "== summary: ${pass} passed, ${fail} failed (production tests skipped) =="
  (( fail == 0 )) || exit 1
  exit 0
fi
echo "  node is up"

echo
echo "== block production (node required) =="
# Use full 32-byte inputs so the returned graffiti is verbatim: with fewer than
# 32 bytes the node may append its client-version watermark into the free space.
#
# Full 32-byte hex is returned exactly.
full_hex="0x1111111111111111111111111111111111111111111111111111111111111111"
expect_graffiti_prefix "full 32-byte hex passes through" "$full_hex" \
  env GRAFFITI="$full_hex"
# Full 32-char string is UTF-8 encoded to exactly 32 bytes and returned exactly.
str32="abcdefghabcdefghabcdefghabcdefgh"
str32_hex="0x$(printf '%s' "$str32" | xxd -p -c 256 | tr -d '\n')"
expect_graffiti_prefix "full 32-char string encodes to hex" "$str32_hex" \
  env GRAFFITI="$str32"
# No graffiti set: the script must still produce a valid block.
if env -u GRAFFITI "$PRODUCE" "$BEACON_URL" 2>/dev/null | jq -e '.data.body.graffiti' >/dev/null; then
  ok "no GRAFFITI still produces a block"
else
  bad "no GRAFFITI still produces a block"
fi

echo
echo "== summary: ${pass} passed, ${fail} failed =="
(( fail == 0 ))
