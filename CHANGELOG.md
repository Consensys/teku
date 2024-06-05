# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Added metadata fields to `/eth/v1/beacon/blob_sidecars/{block_id}` Beacon API response as per https://github.com/ethereum/beacon-APIs/pull/441
- Added rest api endpoint `/teku/v1/beacon/state/finalized/slot/before/{slot}` to return most recent stored state at or before a specified slot.

### Bug Fixes
- Fixed performance degradation introduced in 24.4.0 regarding archive state retrieval time.
