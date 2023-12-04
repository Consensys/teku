# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes
- By default, Teku won't allow syncing from genesis, users should use `--checkpoint-sync-url` when starting a new node. It is possible to revert back to the previous behaviour using the flag `--ignore-weak-subjectivity-period-enabled`.
- Teku will fail to start if the Validator API cannot read the password file used for authorization. Previously, if the Validator API was enabled but Teku couldn't read the password file, it would start but no request to the Validator API would be served. 

### Additions and Improvements
- Support to new Beacon APIs `publishBlindedBlockV2` and `publishBlockV2` which introduce broadcast validation parameter. 
- Added configuration attributes in support of honest validator late block reorg, which adds `REORG_HEAD_WEIGHT_THRESHOLD`, `REORG_PARENT_WEIGHT_THRESHOLD`, and  `REORG_MAX_EPOCHS_SINCE_FINALIZATION` to phase 0 configurations. Mainnet values have been added as defaults for configurations that have not explicitly listed them.
- Added POST `/eth/v1/beacon/states/{state_id}/validators` beacon API.
- Added POST `/eth/v1/beacon/states/{state_id}/validator_balances` beacon API.
- Third party library updates.
- Added `--exit-when-no-validator-keys-enabled` command line option.

### Bug Fixes
