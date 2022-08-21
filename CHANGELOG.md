# Changelog

## Upcoming Breaking Changes
- The `validator_beacon_node_published_attestation_total`, `validator_beacon_node_published_aggregate_total`,
  `validator_beacon_node_send_sync_committee_messages_total`, `validator_beacon_node_send_sync_committee_contributions_total`
  and `validator_beacon_node_published_block_total` metrics have been deprecated in favour of the new `validator_beacon_node_requests_total` metric.
  The old metrics will be removed in a future release. An update to the [Teku Dashboard](https://grafana.com/grafana/dashboards/13457) that uses the new metric is available.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Sets the Bellatrix fork epoch and TTD for The Merge on MainNet 
- Automatically add `/eth/v2/debug/beacon/states/finalized` to the initial state file url (`--initial-state` CLI option) when the provided url doesn't work and doesn't contain any path.
  This simplifies using the standard REST API to retrieve the initial state as just the base URL can be specified (e.g. `--initial-state https://<credentials@eth2-beacon-mainnet.infura.io`)
- Ability to configure multiple beacon nodes for a single validator client using `--beacon-node-api-endpoints` CLI option
- Primed cache for new justified checkpoints to reduce time required to run fork choice immediately after justification
- Remain in optimistic mode when there are no viable branches in the block tree because blocks from every branch were marked INVALID during optimistic sync (https://github.com/ethereum/consensus-specs/pull/2955)
- Added Gas Limit APIs (GET/POST/DELETE)
- Skip finding the PoW block that first satisfies the minimum genesis time condition when the genesis state is already known. Fixes an incompatibility with Nethermind's backwards sync for historic blocks.

### Bug Fixes
- Fixed `NullPointerException` when checking for the terminal PoW block while the EL was syncing
- Changed log level of `Ignoring calculated payload` message to debug level.
- Fixed repeated timeout exceptions when requesting deposit logs from the `--ee-endpoint`
