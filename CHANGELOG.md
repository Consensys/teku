# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Optimized blobs validation pipeline

### Bug Fixes
- Updated the gas change check for block building so that warnings only get raised if the change is off spec.
- Fixed an issue with the `/eth/v1/config/spec` API not returning all previously included configuration parameters.
- Increase the maximum size of a compressed message for libp2p to ensure uncompressed blocks can grow to max size.
