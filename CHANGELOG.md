# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Increased the attestation queue size limits.
- updated graffiti watermarking to put the EL first and shrink the max size.
- Increased the minimum thread count for batch signature verification to 4. If you are on less than a 4 cpu instance, you can override this back to default by using `--Xp2p-batch-verify-signatures-max-threads=2`
- Increased the queue size limit for batch signature verification to 30000.
- Introduced new metric `validator_teku_version_total` which tracks the Teku version in use when running a standalone VC
- Added bootnode-only mode

### Bug Fixes
