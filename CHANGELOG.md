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

### Bug Fixes
