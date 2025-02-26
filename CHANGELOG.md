# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Added beacon-api `/eth/v1/beacon/states/{state_id}/pending_partial_withdrawals` endpoint for use post-electra.
 - Added beacon-api `/eth/v1/beacon/states/{state_id}/pending_deposits` endpoint for use post-electra.
 - Added Chiado Electra configuration due at Mar-06-2025 09:43:40 GMT+0000

### Bug Fixes
 - added 415 response code for beacon-api `/eth/v1/validator/register_validator`.
 - Holesky pectra bad block ignored to aid syncing
