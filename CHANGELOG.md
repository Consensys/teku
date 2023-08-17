# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

- The Development option `--Xp2p-minimum-subnet-subscriptions` has been removed and will no longer be recognised as a command line option.

### Additions and Improvements

- Update attestation subnet subscriptions strategy according to [the spec changes](https://github.com/ethereum/consensus-specs/blob/dev/specs/phase0/p2p-interface.md#attestation-subnet-subscription). All nodes (including non-validating ones) will subscribe to 2 subnets regardless of the number of validators.
- Added `/eth/v1/validator/{pubkey}/voluntary_exit` Validator API endpoint
- Added external signer integration of signing requests for blob sidecar and blinded blob sidecar (Deneb/4844).

### Bug Fixes
