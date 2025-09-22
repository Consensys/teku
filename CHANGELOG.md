# Changelog

## Upcoming Breaking Changes
 - `GOSSIP_MAX_SIZE`, `MAX_CHUNK_SIZE`, `TTFB_TIMEOUT` and `RESP_TIMEOUT` configuration variables will NOT be supported after the Fusaka Mainnet release. These variables should be removed from any custom network configs.

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Added scheduling for Fulu testnets:
  - Holesky fork at epoch 165376 (2025-10-02 12:06:24 UTC)
  - Sepolia fork at epoch 273152 (2025-10-16 14:12:48 UTC)
  - Hoodi fork at epoch 50944 (2025-10-29 22:11:36 UTC)

### Bug Fixes
- `ephemery` network now defaults to loading configuration and bootnodes directly from https://ephemery.dev.

