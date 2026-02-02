# Changelog

## Upcoming Breaking Changes
 
## Current Releases

## Unreleased Changes

### Breaking Changes
 - Removed development cli arg `--Xp2p-reworked-sidecar-recovery-enabled`.

### Additions and Improvements

- New data columns sidecar backfiller enabled. It improves how the beacon node downloads past blobs related data it needs to custody.
- New CLI flag `--rest-api-getblobs-sidecars-download-enabled` allows the beacon node to serve `getBlobs` REST API responses by attempting to fetch missing blob sidecars from the p2p network. The new flag `--rest-api-getblobs-sidecars-download-timeout` controls the network fetch timeout (default: 5 seconds). 


### Bug Fixes

- Added `DOMAIN_BLS_TO_EXECUTION_CHANGE` to spec api output.

