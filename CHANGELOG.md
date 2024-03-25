# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes
 - The development command line argument `--Xvalidator-is-local-slashing-protection-synchronized-enabled` has become a supported and documented argument `--validator-is-local-slashing-protection-synchronized-enabled`.

### Additions and Improvements
- Introduced [Validator Slashing Prevention feature](https://docs.teku.consensys.io/how-to/prevent-slashing/detect-slashing).
- If the EL supports the `engine_getClientVersionV1` Engine API method, the default graffiti (when no graffiti has been configured by the validator) will include EL as well as CL version information. For more details, please see https://github.com/ethereum/execution-apis/pull/517.
- `—-p2p-private-key-file` command line option supports reading a binary private key file.
- Updated libp2p seen cache configuration to reflect EIP-7045 spec changes. This reduces CPU and network bandwidth consumption.
- Increased the attestation cache capacity to allow Teku a bigger pool of attestations when block building.
- Default `--builder-bid-compare-factor` to 90. This makes it necessary for external block builders to give at least 10% additional profit compared to a local build before being taken into consideration. If you would like to go back to the previous default, set `--builder-bid-compare-factor` to 100
- Added `--p2p-direct-peers` command line option to configure explicit peers as per [Explicit Peering Agreements](https://github.com/libp2p/specs/blob/master/pubsub/gossipsub/gossipsub-v1.1.md#explicit-peering-agreements) libp2p spec
- Deposit tree snapshots will be loaded from database as a default unless custom snapshot has been provided.

### Bug Fixes
- Fix incompatibility between Teku validator client and Lighthouse beacon nodes [#8117](https://github.com/Consensys/teku/pull/8117)
