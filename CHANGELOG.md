# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes
- When a lock file is unable to be cleaned up, the (BN or VC) will now exit code 2 in preference to being 'up' but not able to perform duties. This will not self recover and will need intervention from the node operator.

### Additions and Improvements
- Improve block rewards calculation performance for `/eth/v3/validator/blocks/{slot}` block production beacon node API.
- Updated Javalin to v.6 (used by rest-api and keymanager-api).

### Bug Fixes
- Fixed an issue where stale lock files weren't able to be cleaned up and would effectively park the service (BN or VC) with no user errors or any indication that the service was in a bad state.
