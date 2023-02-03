# Changelog

## Upcoming Breaking Changes
- The `--Xmetrics-block-timing-tracking-enabled` option has been renamed to `--metrics-block-timing-tracking-enabled` and enabled by default. The `--X` version will be removed in a future release.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`
- The commandline option `--network` of the `validator-client` subcommand has been undeprecated and can be used to select a network for standalone validator clients. When set to `auto`, it automatically
  fetches network configuration information from the configured beacon node endpoint.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Introduced [Doppelganger Detection feature](https://docs.teku.consensys.net/en/latest/HowTo/Doppelganger-Detection/) 
- Changed color of block production duty to cyan, rather than dark blue.
- Introduced a configuration overview printed during start up.
- Implemented the new [Syncing API update](https://github.com/ethereum/beacon-APIs/pull/290): Introduced a new field `el_offline` in the `GetSyncingStatusResponse` which is set to `true` when execution layer is offline.

### Bug Fixes
- Fixed issue which was causing validator keys to be loaded twice, which, in some cases, was causing beacon node to initialize slower.