# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- By default, Teku won't allow syncing from genesis, users should use `--checkpoint-sync-url` when starting a new node. It is possible to revert back to the previous behaviour using the flag `--ignore-weak-subjectivity-period-enabled`.

### Bug Fixes
