# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Clean up old beacon states when switching from ARCHIVE to PRUNE or MINIMAL data storage mode

### Bug Fixes
 - Fixed a block production issue for Validator Client (24.10.0 to 24.10.2 teku VC), where required headers were not provided for JSON payloads. Default SSZ block production was unaffected. The required header is in the beacon-api spec but was not updated in all places for the VC.
