# Changelog

## Upcoming Breaking Changes

- Upgrading the minimum Java version to Java 17, which means users will need to upgrade their Java installation to at least `Java 17`.  The docker versions relying on `jdk16` will no longer be published, so docker users explicitly referencing the `jdk16` build need to update their package to reference `jdk17`, as tags `develop-jdk16`, `develop-jdk16-arm64`, `latest-jdk16` will no longer be updated.

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

- `--exchange-capabilities-enabled` option has been removed since it is no longer applicable because of [execution-apis](https://github.com/ethereum/execution-apis/pull/418) spec change.
- Removed kiln and ropsten as `--network` options

### Additions and Improvements

- `--validators-external-signer-public-keys` parameter now accepts `external-signer` value. It will enable public key retrieval from external signer standard API, making sure that configured keystore and trustStore will be used, if any.
- Stopped calling `engine_exchangeTransitionConfigurationV1` Engine API method, since the method will be deprecated in the future.

### Bug Fixes

- Fix Get Attestation Rewards API to fetch beacon state instead of block and beacon state (fixes #7338)
