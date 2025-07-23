# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
- Removed `--validators-proposer-blinded-blocks-enabled` unused option. This option is not used anymore and should be removed from any config

### Additions and Improvements
- Increase the default gas limit (`--validators-builder-registration-default-gas-limit`) to 45 million
- Increased the minimum executors for async beacon chain queue to 6 (from 5).

### Bug Fixes
- fix a regression introduced in the previous release causing a validator client configured with sentry nodes to not work properly