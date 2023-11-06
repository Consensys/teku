
# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Updated dependencies.
 - Added `validator_duty_timer` metrics recording the time to perform `block_production`, `attestation_production` and `attestation_aggregation` duties.
 - Updated the default number of threads for batch signature verification based on CPUs available to help larger instances that may have more processing power available.
 - Added `/eth/v3/validator/blocks/{slot}` experimental beacon API for block production

### Bug Fixes
