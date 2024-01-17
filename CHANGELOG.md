# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

- Removed the following hidden feature flags which are no longer
  needed: `--Xfork-choice-update-head-on-block-import-enabled`
  and `--Xbls-to-execution-changes-subnet-enabled`

### Additions and Improvements

- Added a hidden flag `--Xfork-choice-updated-always-send-payload-attributes` which would cause
  payload attributes to be calculated and sent with every fcU. This could be useful for builders
  consuming the `payload_attributes` SSE events.

### Bug Fixes
