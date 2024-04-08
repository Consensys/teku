# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Increased the executor queue default maximum size to 40_000 (previously 20_000), and other queues to 10_000 (previously 5_000). If you have custom settings for these queues, check to ensure they're still required.
- Added `peers_direction_current` libp2p metric to track the number of peers by direction (inbound and outbound).
- Deposit tree snapshots will be loaded from database as a default unless custom snapshot has been provided.
- Added hidden option `--Xdeposit-contract-logs-syncing-enabled` to allow disabling the syncing of the deposit contract logs from the EL. This is useful when running a non-validating node. It is advisable to be used alongside with `--Xeth1-missing-deposits-event-logging-enabled=false` to avoid unnecessary logging of missing deposits.

### Bug Fixes
