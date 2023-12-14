# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Added `--ee-jwt-claim-id` command line option to provide `id` to the execution engine JWT claims

### Bug Fixes
- Fixed the command line help not displaying `--checkpoint-sync-url` as an option. [#7823](https://github.com/Consensys/teku/issues/7823)
- Fixed bug preventing node to startup when using `--exit-when-no-validator-keys-enabled` even with keys present. [#7829](https://github.com/Consensys/teku/pull/7829)
- Fixed bug when node would not start if it failed downloading the deposit snapshot tree from Beacon API [#7827](https://github.com/Consensys/teku/issues/7827). 
