# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
- It is no longer possible to set both `--checkpoint-sync-url` and `--initial-state`. If your node fails to start after upgrade, ensure that only one of these is set.

### Additions and Improvements
- Updated Teku bootnode ENR on Sepolia and Mainnet
- Scheduled Electra for Mainnet at epoch 364032 (May 7, 2025, 10:05:11am UTC)
- Scheduled Electra for Gnosis at epoch 1337856 (April 30, 2025, 14:03:40 UTC)
- Added `--p2p-static-peers-url` option to read static peers from a URL or file

### Bug Fixes
 - It is no longer possible to set both `--checkpoint-sync-url` and `--initial-state`.
