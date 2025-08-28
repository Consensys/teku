# Changelog

## Upcoming Breaking Changes
 - `GOSSIP_MAX_SIZE`, `MAX_CHUNK_SIZE`, `TTFB_TIMEOUT` and `RESP_TIMEOUT` configuration variables will NOT be supported after the Fusaka Mainnet release. These variables should be removed from any custom network configs.

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Enabled, by default, a new attestation pool implementation that improves the attestation packing during block and aggregation production. It can still be disabled by setting `--Xaggregating-attestation-pool-v2-enabled=false` if needed
- Added `--p2p-discovery-bootnodes-url` CLI option.
- Updated `LUKSO` configuration with Electra fork scheduled for epoch `190800` (September 17th, 2025, 16:20:00 UTC)
- Avoid builder validator registration calls potentially delaying block production builder calls.
- Removed `TTFB_TIMEOUT` and `RESP_TIMEOUT` from the default network configurations in line with https://github.com/ethereum/consensus-specs/pull/4532

### Bug Fixes
- Limited the allowed time to wait for fork choice before proceeding with attestation duties, and added a development flag to adjust the timing if required.
- Allowed voluntary exits to be periodically re-broadcast if they have been submitted locally and they have been in the queue for a long time (2 hours).
