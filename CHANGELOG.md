
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
- Removed `/teku/v1/beacon/states/{state_id}`, as the functionality is in the standard state apis.
- The commandline option `--validators-performance-tracking-enabled` has been removed in favour of `--validators-performance-tracking-mode`

### Additions and Improvements
- Upated ropsten testnet config to include extremely high TTD and enable proposer boost.
- Changed the default maximum peers count from 74 to 100 (`--p2p-peer-upper-bound 74` was old setting)
- Update proposer boost weighting to 40%.
- Update `BeaconBlocksByRange` to only return the first block if the step is greater than 1, in line with 1.20 spec.
- Update any calls we make to `BeaconBlocksByRange` to use a step of 1, as step is deprecated in 1.20 spec.
- Added `failOnRejectedCount` query parameter to liveness endpoint.

### Bug Fixes
- Resolve a performance degradation in batch signature verification on machines with multiple, slower CPUs.