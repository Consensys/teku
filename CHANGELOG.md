# Changelog

## Upcoming Breaking Changes

- Upgrading the minimum Java version to Java 17, which means users will need to upgrade their Java installation to at least `Java 17`.  The docker versions relying on `jdk16` will no longer be published, so docker users explicitly referencing the `jdk16` build need to update their package to reference `jdk17`, as tags `develop-jdk16`, `develop-jdk16-arm64`, `latest-jdk16` will no longer be updated.

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

- The command argument `--Xengine-exchange-capabilities` has been removed, update to use `--exchange-capabilities-enabled` if you are using this option.
- The command argument `--Xdeposit-snapshot-enabled` has been removed, just remove it from commandline/configuration if you use it, updated argument `--deposit-snapshot-enabled` defaults to true now.
- The `/eth/v1/debug/beacon/heads` endpoint has been removed in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`.
- The `/eth/v1/validator/blocks/:slot` endpoint has been removed in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`.
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been removed in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been removed in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/validator/liveness/:epoch` endpoint was requiring the wrong body input and now conforms to the beacon-api spec.
- When `--rest-api-enabled` option is not specified and `--rest-api-port` is, `--rest-api-enabled` will now be set as true.

### Additions and Improvements

- Add support for Chiado (Gnosis testnet): `--network=chiado`
- Added beacon-apis rewards endpoints.
- Removed the experimental flag from `/eth/v1/validator/liveness/:epoch` rest-api endpoint.
- Following on from the non finality issue, improved state selection during attestation validation, 
  where we failed to correctly identify that we could use the head state for validation of canonical attestation gossip.

### Bug Fixes

- Fix a race condition on EL api result handling which may lead to beacon node remain syncing forever