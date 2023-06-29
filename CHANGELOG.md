# Changelog

## Upcoming Breaking Changes

- Upgrading the minimum Java version to Java 17, which means users will need to upgrade their Java installation to at least `Java 17`.  The docker versions relying on `jdk16` will no longer be published, so docker users explicitly referencing the `jdk16` build need to update their package to reference `jdk17`, as tags `develop-jdk16`, `develop-jdk16-arm64`, `latest-jdk16` will no longer be updated.

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

- The Development options `--Xprogressive-balances-mode` and `--Xee-version` have been removed and will no longer be recognised as command line options.
- Network configs updated following Consensus Specs changes. If you run custom network, you will need to add [lines with network parameters](https://github.com/Consensys/teku/blob/f80e1de99cdbf50c905682241e24e19291a0881d/ethereum/spec/src/main/resources/tech/pegasys/teku/spec/config/configs/mainnet.yaml#L102-L139) to your custom config including Deneb constants if supported. If you are using a remote validator `auto` network feature, you will need to update both Beacon Node and Validator Client.

### Additions and Improvements

- Added Gnosis configuration for the 🦉 CAPELLA 🦉 network fork due at epoch 648704, UTC Tue 01/08/2023, 11:34:20
- Updated Sigmaprime's bootnodes
- Increased the pending pool size for attestations

### Bug Fixes
