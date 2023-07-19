# Changelog

## Upcoming Breaking Changes

- Upgrading the minimum Java version to Java 17, which means users will need to upgrade their Java installation to at least `Java 17`.  The docker versions relying on `jdk16` will no longer be published, so docker users explicitly referencing the `jdk16` build need to update their package to reference `jdk17`, as tags `develop-jdk16`, `develop-jdk16-arm64`, `latest-jdk16` will no longer be updated.

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

- Upgrading the minimum Java version to Java 17, which means users will need to upgrade their Java installation to at least `Java 17`.  The docker versions relying on `jdk16` will no longer be published, so docker users explicitly referencing the `jdk16` build need to update their package to reference `jdk17`, as tags `develop-jdk16`, `develop-jdk16-arm64`, `latest-jdk16` will no longer be updated.
- The Development option `--Xp2p-minimum-subnet-subscriptions` has been removed and will no longer be recognised as a command line option.

### Additions and Improvements

- Subscribe to a fixed number of attestation subnets ([`SUBNETS_PER_NODE`](https://github.com/ethereum/consensus-specs/blob/2cd967e2bb4f8437cdf70ecd1a8a119a005330a9/configs/mainnet.yaml#L127C7-L127C7)) based on the node id for a period of [`EPOCHS_PER_SUBNET_SUBSCRIPTION`](https://github.com/ethereum/consensus-specs/blob/2cd967e2bb4f8437cdf70ecd1a8a119a005330a9/configs/mainnet.yaml#L112C4-L112C4) epochs

### Bug Fixes
