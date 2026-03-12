# Changelog

## Upcoming Breaking Changes
 
## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

- Added `/eth/v2/node/version` endpoint to retrieve structured version information for both beacon node and execution client.
- Added deprecation warning on startup for any leveldb database types.
- Increased default timeout of Engine API Get Payload requests to 2 seconds.
- Removed quartz scheduler by default, will be removed completely in a future release. Can be re-enabled with `Xquartz-scheduler-enabled=true` (defaults false).

### Bug Fixes