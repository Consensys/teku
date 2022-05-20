
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
- Removed network definition for kintsugi testnet. Support for the execution engine API used in kintsugi was removed in an earlier release.

### Additions and Improvements
- Added support for the ropsten testnet beacon chain.
- Check `Eth1Address` checksum ([EIP-55](https://eips.ethereum.org/EIPS/eip-55)) if address is mixed-case.
- Ignore aggregate attestation and sync contribution gossip that does not include any new validators.
- Optimised BLS batch validation.
- Optimised message ID calculation in jvm-libp2p.
- Reduced memory requirements when sending events on the REST API to many clients.
- Added a labelled metric `executor_rejected_execution_total` to track rejected execution, and updated health check to return 503 if rejected executions are occurring.

### Bug Fixes

- Fix bit length calculation for JSON-deserialized `SszBitvector` objects.