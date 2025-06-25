# Changelog

## Upcoming Breaking Changes
- The `--validators-proposer-blinded-blocks-enabled` is deprecated and will be removed. It's not used anymore and should be removed from the config.

## Current Releases

## Unreleased Changes

### Breaking Changes
- Previous versions of Teku will not be able to read the configuration from this version of Teku (including VC) due to the defaulting of BPO configuration to an empty list.

### Additions and Improvements
- Added `/eth/v1/beacon/states/{state_id}/validator_identities` endpoint to allow querying of validator identities.
- Several improvements on how validator client handles multiple beacon nodes:
  - reduced timeout for beacon node API calls down to 10 seconds
  - improved handling of unresponsive\unreachable beacon nodes.
  - dedicated thread pools for beacon node API calls

### Bug Fixes
- Fixed validator client missing duties when secondary beacon nodes are not responsive. Happens only for validator clients configured with multiple `--beacon-node-api-endpoints`.