# Changelog

## Upcoming Breaking Changes
- Teku currently publishes a `head` event on the REST API 4 seconds into a slot even if a block has not been received. In a future release this will be changed so `head` event is only published when a new
  chain head block is imported. The `--Xvalidators-dependent-root-enabled` option can be used to switch to the new behaviour now for testing.
  Note: this should be applied to both the beacon node and validator client if running separately.
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Additions and Improvements
- Upgraded to use BLS implementation BLST version 0.3.3.
- Teku now waits for Eth1 nodes to finish syncing before requesting data from them. Thanks to Enrico Del Fante.
- Reduced CPU usage required when processing gossip message by optimising peer scoring.
- Added new metrics to Validators
  - `validator_external_signer_requests` with labels `success`, `failed`, `timeout`.
  - `validator_duties_performed` with labels `type` and `result`.
  - `validator_event_stream_disconnections_total` which has a `reason` label. The reason will be one of (disconnect, connect, error, timeout, invalidEvent).
  - `validator_event_stream_invalid_events_total` counts the number of invalid events found on the event stream.
  
### Bug Fixes
- Fixed `ProtoArray: Best node is not viable for head` error.
