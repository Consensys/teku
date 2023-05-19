# Changelog

## Upcoming Breaking Changes

- Upgrading source code to Java 17 meaning users will need to upgrade their Java install to at least 17, or use the jdk17 variant of the docker image.

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

### Additions and Improvements

- Add support for Chiado (Gnosis testnet): `--network=chiado`

### Bug Fixes

- Fix a race condition on EL api result handling which may lead to beacon node remain syncing forever