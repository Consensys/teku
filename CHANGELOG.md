# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
 - Removed `MAX_CHUNK_SIZE` from network global configurations, which will mean older clients will not get it from newer versions of Teku.

### Additions and Improvements
 - Applied spec change to alter `GOSSIP_MAX_SIZE` to `MAX_PAYLOAD_SIZE`.
 - `MAX_PAYLOAD_SIZE` is now used instead of `MAX_CHUNK_SIZE`.
 - Updated 3rd party products to latest versions.
 - Add SSZ support to validator registration via Builder API.
 - Deprecated beacon-api `/eth/v1/config/deposit_contract` - will be removed after electra, in the fulu timeframe.
 - Deprecated beacon-api `/teku/v1/beacon/pool/deposits` - will be removed after electra, in the fulu timeframe.
 - Deprecated beacon-api `/eth/v1/builder/states/{state_id}/expected_withdrawals` - will be removed after electra, in the fulu timeframe.

### Bug Fixes