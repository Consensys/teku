# Changelog

## Upcoming Breaking Changes
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`
- The commandline option `--network` of the `validator-client` subcommand has been undeprecated and can be used to select a network for standalone validator clients. When set to `auto`, it automatically
  fetches network configuration information from the configured beacon node endpoint.
- `--Xbeacon-liveness-tracking-enabled` option will be removed. The `--beacon-liveness-tracking-enabled` option should be used instead (disabled by default)

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- Removed references to beacon block methods `/eth2/beacon_chain/req/beacon_blocks_by_root` and `/eth2/beacon_chain/req/beacon_blocks_by_range`.
- `--Xmetrics-block-timing-tracking-enabled` option has been removed. The `--metrics-block-timing-tracking-enabled` option should be used instead (enabled by default).

### Additions and Improvements
- Trigger the beacon nodes fallback mechanism when the execution layer is offline
- `--beacon-liveness-tracking-enabled` option (disabled by default) can be used instead of `--Xbeacon-liveness-tracking-enabled`
- Added an optional query parameter `locally_submitted` to `/eth/v1/beacon/pool/bls_to_execution_changes` to allow users to query only bls changes submitted to the current node.

### Bug Fixes
- Included All forks in fork schedule if they're defined in configuration.