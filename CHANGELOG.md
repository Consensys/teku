# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

- Upgrading the minimum Java version to Java 17, which means users will need to upgrade their Java installation to at least `Java 17`.  The docker versions relying on `jdk16` will no longer be published, so docker users explicitly referencing the `jdk16` build need to update their package to reference `jdk17`, as tags `develop-jdk16`, `develop-jdk16-arm64`, `latest-jdk16` will no longer be updated.
- Users who make heavy use of API calls to fetch non finalized states data other than head may wish to adjust the states-cache if they see excessive `regeneration of state` messages. This can be accomplished via the `--Xstore-state-cache-size`, which previously defaulted to 160.
- The Development option `--Xp2p-minimum-subnet-subscriptions` has been removed and will no longer be recognised as a command line option.

### Additions and Improvements

- Introduce `--exchange-capabilities-monitoring-enabled` parameter. If enabled, EL will be queried periodically for the Engine API methods it supports. If incompatibility is detected, a warning is raised in the logs. The default is `true`.
- Add support for [Lukso network](https://lukso.network/) `--network=lukso`
- The development option `--Xfork-choice-update-head-on-block-import-enabled` was changed to default to `false` to ensure fork-choice is run when new blocks arrive.
- The default state-cache size has been changed to 8 (previously 160), and there is now an epoch-states-cache, which defaults to a maximum of 6 elements. 
- Update attestation subnet subscriptions strategy according to [the spec changes](https://github.com/ethereum/consensus-specs/blob/dev/specs/phase0/p2p-interface.md#attestation-subnet-subscription). All nodes (including non-validating ones) will subscribe to 2 subnets regardless of the number of validators

### Bug Fixes
