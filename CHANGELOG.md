# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes
- By default, Teku won't allow syncing from genesis, users should use `--checkpoint-sync-url` when starting a new node. It is possible to revert back to the previous behaviour using the flag `--ignore-weak-subjectivity-period-enabled`.
- `validator_remote_beacon_nodes_requests_total` metric for the validator client would not be available if there are no failovers configured. In that case `validator_beacon_node_requests_total` can be used instead.

### Additions and Improvements
- Support to new Beacon APIs `publishBlindedBlockV2` and `publishBlockV2` which introduce broadcast validation parameter. 
- Added configuration attributes in support of honest validator late block reorg, which adds `REORG_HEAD_WEIGHT_THRESHOLD`, `REORG_PARENT_WEIGHT_THRESHOLD`, and  `REORG_MAX_EPOCHS_SINCE_FINALIZATION` to phase 0 configurations. Mainnet values have been added as defaults for configurations that have not explicitly listed them.
- Added POST `/eth/v1/beacon/states/{state_id}/validators` beacon API.
- Added POST `/eth/v1/beacon/states/{state_id}/validator_balances` beacon API.

### Bug Fixes
