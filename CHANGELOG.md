# Changelog

## Upcoming Breaking Changes

- Upgrading the minimum Java version to Java 17, which means users will need to upgrade their Java installation to at least `Java 17`.  The docker versions relying on `jdk16` will no longer be published, so docker users explicitly referencing the `jdk16` build need to update their package to reference `jdk17`, as tags `develop-jdk16`, `develop-jdk16-arm64`, `latest-jdk16` will no longer be updated.

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
 - The Development option `--Xprogressive-balances-mode` has been removed and will no longer be recognised as a command line option.

- Network configs updated following Consensus Specs changes. If you run custom network, you will need to add [lines with network parameters](https://github.com/ConsenSys/teku/blob/475986c523b606c6936d7b4207c1da920ad82ea0/ethereum/spec/src/main/resources/tech/pegasys/teku/spec/config/configs/mainnet.yaml#L98-L125) to your custom config. If you are using a remote validator `auto` network feature, you will need to update both Beacon Node and Validator Client.  

### Additions and Improvements

### Bug Fixes
