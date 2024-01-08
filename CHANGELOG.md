# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Add `proposer_slashing`, `attester_slasing` and `payload_attibutes` (only when a loaded validator will be producing a block) events to the `/eth/v1/events` SSE stream [#7837](https://github.com/Consensys/teku/pull/7837)
- Add Deneb configuration for Goerli [7857](https://github.com/Consensys/teku/pull/7857)

### Bug Fixes
- Fix incompatibility between Teku validator client and Lighthouse beacon nodes [#7842](https://github.com/Consensys/teku/pull/7842)
- Fix a block publishing endpoints issue where `202` status code could be returned but block hasn't been broadcast [#7850](https://github.com/Consensys/teku/pull/7850)
