# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
- Previous versions of teku will not be able to read configuration from this version of teku (including VC) due to BPO configuration being added.

### Additions and Improvements

- Added `--p2p-static-peers-url` option to read static peers from a URL or file
- Added node epoch and computed slot to the sync committee duties failure message for more context about the failure condition.
- Updated third party libraries.
- Added an info message on startup for the highest supported milestone and associated epoch.
- Added JDK 24 docker image build.
- Reduced block building time at the first slot of an epoch when a large number of validators are configured.
- Improved configuration loading to use builtin configurations to default any fields we need that were missing from a passed in configuration.
- Add `/teku/v1/admin/add_peer` endpoint to allow adding static peers via the REST API.

### Bug Fixes
 - Added an error if the genesis state has invalid data in its latest block header.