
# Changelog

## Upcoming Breaking Changes
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- The commandline option `--validators-performance-tracking-enabled` has been removed in favour of `--validators-performance-tracking-mode`

### Additions and Improvements
- Reduced memory requirements for storing the deposit merkle tree.
- Enable spec change to ignore weightings from attestations from equivocating validators.
- Fork choice before proposals is now enabled by default for testnets. It can be disabled with `--Xfork-choice-before-proposing-enabled=false` if required.
- Updated the Ropsten network configuration to include the correct terminal difficulty.
- Updated to BLST 0.3.8

### Bug Fixes
- Fixed issue where the REST API may return content as SSZ instead of JSON if the header `Accept: */*` was specified.
- Fixed issue where sync committee aggregations were skipped, but reported failed because there were no signatures to aggregate.
- Fixed division by zero when estimating time to TTD during a period with no new PoW blocks.
- Fixed issue where validator client could receive a 500 rather than 503 when getting duties if the beacon node was syncing.
