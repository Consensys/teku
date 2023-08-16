# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

- The Development option `--Xp2p-minimum-subnet-subscriptions` has been removed and will no longer be recognised as a command line option.

### Additions and Improvements

- Update attestation subnet subscriptions strategy according to [the spec changes](https://github.com/ethereum/consensus-specs/blob/dev/specs/phase0/p2p-interface.md#attestation-subnet-subscription). All nodes (including non-validating ones) will subscribe to 2 subnets regardless of the number of validators.
- The latest version of [blst](https://github.com/supranational/blst) will automatically use optimized code paths if they are supported. As a result, `JAVA_OPTS="-Dteku.portableBlst=true"` is no longer necessary for some older systems.

### Bug Fixes
