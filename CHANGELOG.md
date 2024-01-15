# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

- Added a hidden flag `--Xfork-choice-updated-always-send-payload-attributes` which would cause
payload attributes to be calculated and sent with every fcU. This could be useful for builders
consuming the `payload_attributes` SSE events.
- Added Deneb (aka Dencun) configuration for Holesky network fo epoch 29696 (2024-02-07 11:34:24 UTC).

### Bug Fixes
