# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Updated dependencies.
 - Added `validator_duty_timer` metrics recording the time to perform `block_production`, `attestation_production` and `attestation_aggregation` duties. Please note that this metric is not available by default and needs to be enabled using the [`--metrics-categories`](https://docs.teku.consensys.net/development/reference/cli#metrics-categories) CLI option.
 - Updated the default number of threads for batch signature verification based on CPUs available to help larger instances that may have more processing power available.
 - Added `/eth/v3/validator/blocks/{slot}` experimental beacon API for block production
 - Added [`--checkpoint-sync-url`](https://docs.teku.consensys.net/reference/cli#checkpoint-sync-url) CLI option.
 - Added `--allow-no-loaded-keys` CLI option allowing the VC to run with no loaded keys.

### Bug Fixes
