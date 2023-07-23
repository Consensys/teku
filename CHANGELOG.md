# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

- Upgrading the minimum Java version to Java 17, which means users will need to upgrade their Java installation to at least `Java 17`.  The docker versions relying on `jdk16` will no longer be published, so docker users explicitly referencing the `jdk16` build need to update their package to reference `jdk17`, as tags `develop-jdk16`, `develop-jdk16-arm64`, `latest-jdk16` will no longer be updated.

### Additions and Improvements

- Introduce `--exchange-capabilities-monitoring-enabled` parameter. If enabled, EL will be queried periodically for the Engine API methods it supports. If incompatibility is detected, a warning is raised in the logs. The default is `true`.

### Bug Fixes
