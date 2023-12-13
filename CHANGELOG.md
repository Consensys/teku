# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Add `proposer_slashing`, `attester_slasing` and `payload_attibutes` (only when a loaded validator will be producing a block) events to the `/eth/v1/events` SSE stream
- Added `--stop-vc-when-validator-slashed` option to stop the VC when a validator is slashed

### Bug Fixes
- Fix incompatibility between Teku validator client and Lighthouse beacon nodes
