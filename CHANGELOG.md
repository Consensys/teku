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
- Basic authentication is now supported for `--initial-state`. Infura can now be used as the source of initial states with `--initial-state https://{projectid}:{secret}@eth2-beacon-mainnet.infura.io/eth/v1/debug/beacon/states/finalized`
- Implement standard rest api `/eth/v2/beacon/blocks/:block_id` which supports altair blocks. Documented under 'Experimental' endpoints until more widely implemented.
- Implement teku rest api to retrieve all blocks at a slot `/teku/v1/beacon/blocks/:slot`, which will return both canonical and non-canonical blocks at a given slot.

### Bug Fixes
- Fixed issue where attestation subnets were not unsubscribed from leading to unnecessary CPU load when running small numbers of validators.
- Fixed issue where validator duties were not invalidated in response to new blocks correctly when using dependent roots.
- Fixed issue where exit epoch for voluntary exits would be incorrectly calculated.
