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
- Include expected path for keystore password file in error message when password file is not found.
- Added additional bootnodes for Pyrmont testnet.
- Optimised how block production metrics are calculated.
- implement GET `/eth/v1/node/peer_count` standard api endpoint.
- When handling blocksByRange requests that target blocks we haven't yet downloaded, return the standard "resource unavailable" response code (3) rather than a custom response code.
- Remove legacy pure Java BLS cryptography implementation (Mikuli).
- Added `beacon_eth1_requests_total` metric to report the number of requests sent to eth1 endpoints.
- Rework network configuration parsing to accept the new config format.  For details on the new format, see the [eth2.0-specs repo](https://github.com/ethereum/eth2.0-specs/pull/2390).  With this change, we no longer support pointing to directories for the network configuration.  Now, the network config (supplied via `--network`) should always point to a single yaml file.
- For Altair networks, `--p2p-subscribe-all-subnets-enabled` will subscribe to all subcommittee subnets.
- Gradle build enhancements. Upgraded gradle and various plugin versions. Introduced new license dependency report generator with custom renderer. Refactored custom errorprone checks to its own repo. The project can now be built against JDK 16.
- Implement alpha.7 spec updates to sync committee logic and rewards

### Bug Fixes
- Fixed failures in the `checkMavenCoordinateCollisions` task if it was run prior to running spotless.
- Use system default character set for console output rather than forcing UTF-8. Avoids corrupting characters on systems using charsets that are not ascii based.
- Fixed a `NullPointerException` from validator clients for new networks, prior to genesis being known.
- Fixed regression where eth_getLogs responses from Infura that rejected the request because they returned too many logs did not retry the request with a smaller request range.
- Experimental: Fix for eth1 follow distance tracking revealed in Rayonism testnets. Teku incorrectly strictly follows 2048 blocks before the eth1 head but the follow distance should be based on timestamp, not block number.
  An experimental fix for this can be enabled with `--Xeth1-time-based-head-tracking-enabled`. Further testing will be conducted before enabling this by default.
- Prevent LevelDB transactions from attempting to make any updates after the database is shut down

### Experimental: New Altair REST APIs
- implement POST `/eth/v1/beacon/pool/sync_committees` to allow validators to submit sync committee signatures to the beacon node.
- implement POST `/eth/v1/validator/duties/sync/{epoch}` for Altair fork.
- implement GET and POST `/eth/v1/validator/sync_committee_subscriptions` for Altair fork.
- implement GET `/eth/v2/validator/blocks/{slot}` for Altair fork.
- implement GET `/eth/v2/debug/beacon/states/:state_id` for Altair fork.
- implement GET `/eth/v1/beacon/states/{state_id}/sync_committees` for Altair fork.
- `/eth/v1/validator/blocks/{slot}` will now produce an altair block if an altair slot is requested.
- `/eth/v1/node/identity` will now include syncnets in metadata
