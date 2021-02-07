# Changelog

## Upcoming Breaking Changes
- Binary downloads will be transitioned from Bintray to Cloudsmith.  Please ensure you use links in the documentation or release notes.
  Ansible users should ensure they have the latest version of the ansible role.
  To ensure a smooth migration, releases are currently published to both Cloudsmith and Bintray but support for Bintray will be dropped in the near future.
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.
- Docker images are now being published to `consensys/teku`. The `pegasys/teku` images will continue to be updated for the next few releases but please update your configuration to use `consensys/teku`.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- Teku no longer publishes a `head` event on the REST API 4 seconds into a slot even if a block has not been received. The `head` event is only published when a new
  chain head block is imported. The `--Xvalidators-dependent-root-enabled=false` option can be used to revert to the old behaviour.
  Note: this should be applied to both the beacon node and validator client if running separately.
- The default docker image now uses Java 15. Java 14 based images are available with the `-jdk14` suffix if required (e.g `consensys/teku:develop-jdk14`)
- `--validators-key-files` and `--validators-key-password-files` have now been removed. They are replaced by `--validator-keys`.

### Additions and Improvements
- `--p2p-nat-method upnp` has been added to allow users to use upnp to configure firewalls to allow incoming connection requests.
- `--initial-state` argument is now ignored if chain data is already initialised. Previously it would be downloaded on every restart and Teku would exit if the referenced state was ahead of the chain on disk.
- Enabled the new sync algorithm by default. This improves sync behaviour when there are multiple forks and distributes requests for blocks across available peers. The old sync algorithm can still be used by setting `--Xp2p-multipeer-sync-enabled=false`.
- The validator client now uses an independent timer to trigger attestation creation instead of depending on the beacon node publishing a `head` event 4 seconds into the slot. By default the beacon node no longer publishes a `head` event for empty slots. 
  The previous behaviour can be restored with `--Xvalidators-dependent-root-enabled=false`. Note: this should be applied to both the beacon node and validator client if running separately.
- The default docker image has been upgraded to use Java 15.
- Updated the ENRs for MainNet bootnodes run by the Nimbus team.
- Added a `is_syncing` field to the `/eth/v1/node/syncing` endpoint to explicitly indicate if the node is in sync or not. 
- Teku will now exit when database writes fail (e.g. due to disk failure) to enable systems like kubernetes to better identify and respond to the failure.

### Bug Fixes
- Ensured shutdown operations have fully completed prior to exiting the process.
