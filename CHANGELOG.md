# Changelog

## Upcoming Breaking Changes
- The `--validators-proposer-blinded-blocks-enabled` is deprecated and will be removed. It's not used anymore and should be removed from any configs.

## Current Releases

## Unreleased Changes

### Breaking Changes
- Previous versions of Teku will not be able to read the configuration from this version of Teku (including VC) due to the defaulting of BPO configuration to an empty list.

### Additions and Improvements
- Added `/eth/v1/beacon/states/{state_id}/validator_identities` endpoint to allow querying of validator identities.
- Increase the default gas limit (`--validators-builder-registration-default-gas-limit`) to 45 million

### Bug Fixes
