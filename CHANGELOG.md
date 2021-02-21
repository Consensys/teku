# Changelog

## Upcoming Breaking Changes
- Binary downloads will be transitioned from Bintray to Cloudsmith.  Please ensure you use links in the documentation or release notes.
  Ansible users should ensure they have the latest version of the ansible role.
  To ensure a smooth migration, releases are currently published to both Cloudsmith and Bintray but support for Bintray will be dropped in the near future.
- Teku currently publishes a `head` event on the REST API 4 seconds into a slot even if a block has not been received. In a future release this will be changed so `head` event is only published when a new
  chain head block is imported. The `--Xvalidators-dependent-root-enabled` option can be used to switch to the new behaviour now for testing.
  Note: this should be applied to both the beacon node and validator client if running separately.
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes
- Pull in latest jvm-libp2p release (0.7.0).
- Delay gossip topic subscription until the node is nearly in sync.

### Breaking Changes
- The default docker image now uses Java 15. Java 14 based images are available with the `-jdk14` suffix if required (e.g `consensys/teku:develop-jdk14`)
- `--validators-key-files` and `--validators-key-password-files` have now been removed. They are replaced by `--validator-keys`.
- Docker images are now being published to `consensys/teku`. The `pegasys/teku` images are no longer updated.

### Additions and Improvements
- `--p2p-nat-method upnp` has been added to allow users to use upnp to configure firewalls to allow incoming connection requests.
- `--initial-state` argument is now ignored if chain data is already initialised. Previously it would be downloaded on every restart and Teku would exit if the referenced state was ahead of the chain on disk.
- Enabled the new sync algorithm by default. This improves sync behaviour when there are multiple forks and distributes requests for blocks across available peers. The old sync algorithm can still be used by setting `--Xp2p-multipeer-sync-enabled=false`.
- The default docker image has been upgraded to use Java 15.
- Updated the ENRs for MainNet bootnodes run by the Nimbus team.
- Added a `is_syncing` field to the `/eth/v1/node/syncing` endpoint to explicitly indicate if the node is in sync or not. 
- Teku will now exit when database writes fail (e.g. due to disk failure) to enable systems like kubernetes to better identify and respond to the failure.
- Reduced time required to load local validator keys at startup.

### Bug Fixes
- Ensured shutdown operations have fully completed prior to exiting the process.
- Fixed `NoSuchElementException` that occurred during syncing.
- Avoid marking the node as in sync incorrectly if an error occurs while syncing. Now selects a new target chain and continues syncing.
- Reject validator related REST API requests when syncing, even if the head block is close to the current slot.  `head` and `chain_reorg` events do not fire while sync is active so the validator client is unable to detect when it should invalidate duties.
- Increase the default limit for the queue for delivering events to REST API subscribers. When subscribing to attestations, they are often received in bursts which would exceed the previous limit even when the client was keeping up.
  The default limit is now 250 and can now be configured with `--Xrest-api-max-pending-events`.
- Fixed `ProtoNode: Delta to be subtracted is greater than node weight` exception which may occur after an issue writing data to disk storage.
- Fix issue where basic authentication credentials included in the `--beacon-node-api-endpoint` URL were included in `DEBUG` level logs.
- Proposer duties are now consistently reported in slot order in the REST API.