# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
 - Removed `MAX_CHUNK_SIZE` from network global configurations, which will mean older clients will not get it from newer versions of Teku.

### Additions and Improvements
 - Applied spec change to alter `GOSSIP_MAX_SIZE` to `MAX_PAYLOAD_SIZE`.
 - `MAX_PAYLOAD_SIZE` is now used instead of `MAX_CHUNK_SIZE`.
 - Add SSZ support to validator registration via Builder API.

### Bug Fixes