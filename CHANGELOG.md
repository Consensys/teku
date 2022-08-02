# Changelog

## Upcoming Breaking Changes
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - MEV-Boost\Builder support
 - Enables fork choice before block proposals by default on MainNet (previously on by default on testnets only)
 - Optimisations in jvm-libp2p to reduce CPU usage
 - Updated Sepolia bootnodes
 - Enabled progressive balance tracking optimisation on MainNet
 - Replaced separate metrics for each beacon node request with one metric `beacon_node_requests_total`. Some old metrics were kept for backwards compatibility, but will be removed in future versions.

### Bug Fixes
 - `--ee-endpoint` option was not used to retrieve deposits for networks where Bellatrix was not yet scheduled
 - Fix `latestValidHash`with invalid Execution Payload in response from execution engine didn't trigger appropriate ForkChoice changes 
 - Remove incorrect error about potentially finalizing an invalid execution payload when importing a block with an invalid payload
