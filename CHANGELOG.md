
# Changelog

## Upcoming Breaking Changes
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`
- The commandline option `--validators-performance-tracking-enabled` has been deprecated in favour of `--validators-performance-tracking-mode`
 
## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- For Bellatrix fork and later, send `block_header` instead of `block` for external signer block signing request (`BLOCK_V2`).
- Check address checksum for `--eth1-deposit-contract-address` and `--validators-proposer-default-fee-recipient`.

### Additions and Improvements
- Improved performance when regenerating non-finalized states that had to be dropped from memory.
- Performance optimizations for Gnosis beacon chain.
- Improved performance when processing epoch transitions.
- Added `is_optimistic` field to `/eth/v1/node/syncing` response.
- Using execution engine endpoint as Eth1 endpoint when latter is not provided.
- Check `Eth1Address` mixed-case checksum as defined by [EIP-55](https://eips.ethereum.org/EIPS/eip-55).

### Bug Fixes
- Added stricter limits on attestation pool size. 
- Fixed issue with loading the optimised BLST library on Windows.
- Reduced log level for notifications that the eth1 chain head could not be retrieved because no endpoints were available.
- Fixed issue where logging options were not recognised if specified after the `validator-client` subcommand.
- Avoid disconnecting event stream connections subscribed to attestation events for briefly exceeding the maximum pending event queue size. A very large number of attestations are received all at once on MainNet making it almost impossible for a consumer to stay below the queue size limit at all times.