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
- Early access: Support for automatic fail-over of eth1-endpoints.  Multiple endpoints can be specified with the new `--eth1-endpoints` CLI option. Thanks to Enrico Del Fante.
- implement standard rest api `/eth/v2/beacon/blocks/:block_id` which supports altair blocks.

### Bug Fixes
- Fixed issue where attestation subnets were not unsubscribed from leading to unnecessary CPU load when running small numbers of validators.
- Fixed issue where validator duties were not invalidated in response to new blocks correctly when using dependent roots.