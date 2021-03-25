# Changelog

## Upcoming Breaking Changes
- Teku currently publishes a `head` event on the REST API 4 seconds into a slot even if a block has not been received. In a future release this will be changed so `head` event is only published when a new
  chain head block is imported. The `--Xvalidators-dependent-root-enabled` option can be used to switch to the new behaviour now for testing.
  Note: this should be applied to both the beacon node and validator client if running separately.
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes
  
### Breaking Changes
- The events api `/eth/v1/events` - `block` event now returns the slot and root as detailed in the standard API specification, instead of the entire block.

### Additions and Improvements
- When downloading the `--initial-state` from a URL, the `Accept: application/octet-stream` header is now set. This provides compatibility with the standard API `/eth/v1/debug/beacon/states/:state_id` endpoint.
- `--ws-checkpoint` CLI now accepts a URL optionally, and will load the `ws_checkpoint` field from that URL.
- Reduced CPU usage by avoiding creation of REST API events when there are no subscribers.

### Bug Fixes
- Fixed issue in discv5 where nonce was incorrectly reused.
- Block events now only return the slot and root, rather than the entire signed block.