# Changelog

## Upcoming Breaking Changes
 
## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

- New CLI flag `--rest-api-getblobs-sidecars-download-enabled` allows the beacon node to serve `getBlobs` REST API responses by attempting to fetch missing blob sidecars from the p2p network. The new flag `--rest-api-getblobs-sidecars-download-timeout` controls the network fetch timeout (default: 5 seconds).
- Migrated `DataColumnSidecars` out of the database, they are now stored in `beacon/columns`, and any existing data will migrate when the node starts up.

### Bug Fixes

- added `DOMAIN_BLS_TO_EXECUTION_CHANGE` to spec api output.

