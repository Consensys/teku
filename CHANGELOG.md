# Changelog

## Upcoming Breaking Changes
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Added command line option `--validators-early-attestations-enabled`, which defaults to true. 
   When using a load balanced beacon node, this option should be disabled.
 - Added additional bootnodes for the Prater testnet to improve peer discovery.
 - Improved peer discovery. All authenticated node sessions are evaluated as potential peers to connect.
 - Improved clarity of sync progress log messages.

### Bug Fixes
 - Added a column size and percentage complete to migrate-database command, where columns contain block or state objects, as they can be time consuming to copy.
 - Fixed issue in Altair where sync committee contribution gossip could be incorrectly rejected when received at the very end of the slot.

