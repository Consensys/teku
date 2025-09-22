# Changelog

## Upcoming Breaking Changes
 - `GOSSIP_MAX_SIZE`, `MAX_CHUNK_SIZE`, `TTFB_TIMEOUT` and `RESP_TIMEOUT` configuration variables will NOT be supported after the Fusaka Mainnet release. These variables should be removed from any custom network configs.

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Added scheduling for Fulu testnets:
  - Holesky fork at epoch 165120 (2025-10-01 08:48:00 UTC)
  - Sepolia fork at epoch 272640 (2025-10-14 07:36:00 UTC)
  - Hoodi fork at epoch 50688 (2025-10-28 18:53:12 UTC)

### Bug Fixes
- `ephemery` network now defaults to loading configuration and bootnodes directly from https://ephemery.dev.

