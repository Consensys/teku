# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Increased the executor queue default maximum size to 40_000 (previously 20_000), and other queues to 10_000 (previously 5_000). If you have custom settings for these queues, check to ensure they're still required.
- Added `peers_direction_current` LIBP2P metric for the number of peers by direction including inbound and outbound.

### Bug Fixes
