# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

- Updated counter metrics to incorporate the suffix `_total`. If you are using a custom dashboard to monitor Teku metrics, you might need to update the metrics manually when breaking changes are introduced. For more information, see [Update metrics](../../how-to/monitor/update-metrics.md).

### Additions and Improvements
- Added a state pruner that can limit the number of finalized states stored when running an archive node.

- Updated bootnodes for Sepolia network.

- Updated a number of parameters to reduce issues when using `p2p-subscribe-all-subnets-enabled`. If you have adjusted queue sizes manually when using all-subnets, please refer to details below. Manual settings will still override these defaults.
- When `p2p-subscribe-all-subnets-enabled`, `p2p-peer-lower-bound` now defaults to 60 (previously 64), and `p2p-peer-upper-bound` now defaults to 80 (previously 100).
- When `p2p-subscribe-all-subnets-enabled`,  (`Xnetwork-async-p2p-max-queue`, `Xnetwork-async-beaconchain-max-queue`, `Xp2p-batch-verify-signatures-queue-capacity`)  now default to 40_000 (previously 10_000)
- When `p2p-subscribe-all-subnets-enabled`,  `Xvalidator-executor-max-queue-size`  now defaults to 60_000 (previously 40_000).

### Bug Fixes
