
# Changelog

## Upcoming Breaking Changes
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`
- The commandline option `--validators-performance-tracking-enabled` has been deprecated in favour of `--validators-performance-tracking-mode`
 
## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- The `/eth/v1/node/peers` endpoint had field `address` in `data` object, which is not correct according to the spec, changed to `last_seen_p2p_address`. Also `meta` object with `count` added
- The `/eth/v1/node/peers/{peer_id}` endpoint had field `address` in `data` object, which is not correct according to the spec, changed to `last_seen_p2p_address`

### Additions and Improvements
- Introduced smarter state selection strategy when validating attestations to reduce required regenerations after a full GC.
- Improved peer scoring to better handle temporary errors from peers.
- Enabled fork choice proposer boost by default.
- Added Websockets and IPC protocols support for execution client’s Engine JSON RPC API.
- Added experimental support for Gnosis Beacon chain

### Bug Fixes
- Fixed the target database format for the `migrate-database` command.