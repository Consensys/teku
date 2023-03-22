# Changelog

## Upcoming Breaking Changes
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`
- The `--p2p-discovery-site-local-addresses-enabled` option will be set to `false` by default. If you use the client's discovery inside the local network, update its launch command to toggle the option.  

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- `--Xbeacon-liveness-tracking-enabled` has been removed in favour of `--beacon-liveness-tracking-enabled`

### Additions and Improvements
- The commandline option `--network` of the `validator-client` subcommand has been **un**deprecated for values other than `auto`.
- Send validator registrations to the Beacon node when the Validator client has reconnected to the event stream
- Added optional query parameters `require_prepared_proposers` and `require_validator_registrations` to the `teku/v1/admin/readiness` endpoint
- Added mainnet configuration for CAPELLA network fork due epoch 194048, April 12, 2023; 10:27:35pm UTC
- Added experimental feature `--Xdeposit-snapshot-enabled` to use bundled deposit contract tree snapshot and persist it after finalization to decrease EL pressure and speed up node startup
- Added `--p2p-discovery-site-local-addresses-enabled` option to allow discovery connections to local (RFC1918) addresses.
- Improved `Completed regeneration` info message so that is more technically accurate.

### Bug Fixes
- Fixed a bug in signature verification on fork boundaries when first blocks of the new fork are missing.
