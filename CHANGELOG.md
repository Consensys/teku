# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

- The response of the Attestation Rewards method was updated to include two extra
  fields (`inactivity` and `inclusion_delay`) into the `total_rewards` and `ideal_rewards` objects.
  This should be considered a breaking change only if you are strictly
  checking the structure of the json in the response.

### Additions and Improvements

- Solve an unintended breaking change in `23.9.1` release which was including an updated leveldb
  native library not compatible with relative old Linux distributions. Leveldb native library has
  been updated to support older GLIBC versions (ie Ubuntu 20.04 and Debian 11).

### Bug Fixes
- During network configuration load, all missing fields will now be reported, rather than just the first missing field causing failure.
