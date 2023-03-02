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

### Additions and Improvements
- Trigger the beacon nodes fallback mechanism when the execution layer is offline
- `--beacon-liveness-tracking-enabled` option (disabled by default) can be used instead of `--Xbeacon-liveness-tracking-enabled`
- Added an optional query parameter `locally_submitted` to `/eth/v1/beacon/pool/bls_to_execution_changes` to allow users to query only bls changes submitted to the current node.
- Added `/eth/v1/builder/states/{state_id}/expected_withdrawals` rest api endpoint.
- Added `/eth/v1/beacon/rewards/sync_committee/{block_id}` rest api endpoint.
- Added Capella fork information for Goerli network configuration.

### Bug Fixes
- Included All forks in fork schedule if they're defined in configuration.