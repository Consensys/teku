# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Enabling, by default, a new attestation pool implementation that improves the attestation packing during block and aggregation production. It can still be disabled by setting `--Xaggregating-attestation-pool-v2-enabled=false` if needed
- Added `--p2p-discovery-bootnodes-url` CLI option.
- Avoid builder validator registration calls potentially delaying block production builder calls.

### Bug Fixes
