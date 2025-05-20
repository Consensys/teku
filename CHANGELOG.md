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
- Added jdk 24 docker image build.
- Improved performance when scheduling attestations in the beginning of the epoch for a large number of validators.
- Improved configuration loading to use builtin configurations to default any fields we need that were missing from a passed in configuration.

### Bug Fixes