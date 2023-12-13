# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

- Removed the following hidden feature flags which are no longer
  needed: `--Xfork-choice-update-head-on-block-import-enabled`
  and `--Xbls-to-execution-changes-subnet-enabled`

### Additions and Improvements

- Added a hidden flag `--Xfork-choice-updated-always-send-payload-attributes` which would cause 
  payload attributes to be calculated and sent with every fcU. This could be useful for builders
  consuming the `payload_attributes` SSE events.
- Added Deneb (aka Dencun) configuration for Sepolia network for epoch 132608 (2024-01-30 22:51:12 UTC).
- Added Deneb (aka Dencun) configuration for Chiado network for epoch 516608 (2024-01-31 18:15:40 UTC).
- Added Deneb (aka Dencun) configuration for Holesky network for epoch 29696 (2024-02-07 11:34:24 UTC).
- Generate key at `—p2p-private-key-file` path if specified file doesn't exist.
- Added `--stop-vc-when-validator-slashed` option to stop the VC when a validator is slashed

### Bug Fixes
