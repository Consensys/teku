# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Improved block publishing performance, especially relevant with locally produced blocks
- Delayed blob publishing until the block is published to at least 1 peer, especially relevant with locally produced blocks with low upload bandwidth connections. Can be disabled via `--Xp2p-gossip-blobs-after-block-enabled=false`
- Added single_attestation event type for electra attestations

### Bug Fixes
- Added a startup script for unix systems to ensure that when jemalloc is installed the script sets the LD_PRELOAD environment variable to the use the jemalloc library
