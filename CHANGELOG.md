# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

- The Development option `--Xp2p-minimum-subnet-subscriptions` has been removed and will no longer be recognised as a command line option.
- If running separated Beacon Node and Validator node it is required to upgrade Beacon Node first and then Validator Node. A Validator Node running this release won't start if connecting to an older Beacon Node version.

### Additions and Improvements

- New databases will now default to `minimal` if no `--data-storage-mode` is explicitly set. Existing `prune` mode databases will need to explicitly set `--data-storage-mode=minimal` if they wish to use minimal data storage. This includes anyone not specifying `--data-storage-mode` in 23.6.2 or earlier.
- Update attestation subnet subscriptions strategy according to [the spec changes](https://github.com/ethereum/consensus-specs/blob/dev/specs/phase0/p2p-interface.md#attestation-subnet-subscription). All nodes (including non-validating ones) will subscribe to 2 subnets regardless of the number of validators.
- The latest version of [blst](https://github.com/supranational/blst) will automatically use optimized code paths if they are supported. As a result, `JAVA_OPTS="-Dteku.portableBlst=true"` is no longer necessary for some older systems.
- Added `/eth/v1/validator/{pubkey}/voluntary_exit` Validator API endpoint
- Add support for Holesky test network `--network=holesky`
- Add support for gzip encoding in REST API

### Bug Fixes
- Fixed a bug in network configuration loader which was ignoring MIN_EPOCHS_FOR_BLOCK_REQUESTS parameter. 
