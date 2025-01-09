# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
`--Xvalidators-builder-registration-default-gas-limit` has been removed in favour of `--Xvalidators-builder-registration-default-gas-limit`

### Additions and Improvements
- Optimized blobs validation pipeline
- Remove delay when fetching blobs from the local EL on block arrival
- Default the gas limit for externally produced blocks to 36 million

### Bug Fixes
- Fix `--version` command output [#8960](https://github.com/Consensys/teku/issues/8960)
- Fix issue (introduced in `24.12.1`) with peer stability when the upperbound is set to a high number