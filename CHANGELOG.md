# Changelog

## Upcoming Breaking Changes

- Upgrading the minimum Java version to Java 17, which means users will need to upgrade their Java installation to at least `Java 17`.  The docker versions relying on `jdk16` will no longer be published, so docker users explicitly referencing the `jdk16` build need to update their package to reference `jdk17`, as tags `develop-jdk16`, `develop-jdk16-arm64`, `latest-jdk16` will no longer be updated.

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

- `--exchange-capabilities-enabled` option has been removed since it is no longer applicable because of [execution-apis](https://github.com/ethereum/execution-apis/pull/418) spec change.
- Removed kiln and ropsten as `--network` options
- The Development option `--Xp2p-minimum-subnet-subscriptions` has been removed and will no longer be recognised as a command line option.

### Additions and Improvements

- `--validators-external-signer-public-keys` parameter now accepts `external-signer` value. It will enable public key retrieval from external signer standard API, making sure that configured keystore and trustStore will be used, if any.
- Stopped calling `engine_exchangeTransitionConfigurationV1` Engine API method, since the method will be deprecated in the future.
- Subscribe to a fixed number of attestation subnets ([`SUBNETS_PER_NODE`](https://github.com/ethereum/consensus-specs/blob/2cd967e2bb4f8437cdf70ecd1a8a119a005330a9/configs/mainnet.yaml#L127C7-L127C7)) based on the node id for a period of [`EPOCHS_PER_SUBNET_SUBSCRIPTION`](https://github.com/ethereum/consensus-specs/blob/2cd967e2bb4f8437cdf70ecd1a8a119a005330a9/configs/mainnet.yaml#L112C4-L112C4) epochs

### Bug Fixes

- Fix Get Attestation Rewards API to fetch beacon state instead of block and beacon state (fixes #7338)
