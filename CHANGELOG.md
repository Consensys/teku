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
- Newly created databases will now use LevelDB for storage instead of RocksDB. This uses less memory and has proven to be more stable. Existing databases are unaffected and will continue to use RocksDB.
- Support for automatic fail-over of eth1-endpoints.  Multiple endpoints can be specified with the new `--eth1-endpoints` CLI option. Thanks to Enrico Del Fante.
- implement POST `/eth/v1/validator/duties/sync/{epoch}` for Altair fork.

### Bug Fixes
