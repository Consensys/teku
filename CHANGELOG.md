# Changelog

## Upcoming Breaking Changes
- The `--Xmetrics-block-timing-tracking-enabled` option has been renamed to `--metrics-block-timing-tracking-enabled` and enabled by default. The `--X` version will be removed in a future release. 
- The `validator_beacon_node_published_attestation_total`, `validator_beacon_node_published_aggregate_total`,
  `validator_beacon_node_send_sync_committee_messages_total`, `validator_beacon_node_send_sync_committee_contributions_total`
  and `validator_beacon_node_published_block_total` metrics have been deprecated in favour of the new `validator_beacon_node_requests_total` metric.
  The old metrics will be removed in a future release. An update to the [Teku Dashboard](https://grafana.com/grafana/dashboards/13457) that uses the new metric is available.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`
- The commandline option `--network` of the `validator-client` subcommand has been undeprecated and can be used to select a network for standalone validator clients. When set to `auto`, it automatically
  fetches network configuration information from the configured beacon node endpoint.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- The logic with which the default configuration is applied when using `validators-proposer-config` has been updated.
  The logic is now more intuitive but a given proposer config file may result in a different configuration compared with the previous Teku version.
  (Refer to https://github.com/ConsenSys/teku/pull/6325#issue-1409631918 for more information).  

### Additions and Improvements
 - Updated protobuf used by libp2p library to resolve a potential DoS vector
 - Improved discv5 compliance
 - Changed the builder `is online\is offline` logs to `is available\is not available`
 - Updated REST API to use Javalin 5
 - Added `/eth/v1/beacon/states/{state_id}/randao` to beacon-api.
 - Block timing tracking is now enabled by default. The `--Xmetrics-block-timing-tracking-enabled` option has been renamed to `--metrics-block-timing-tracking-enabled`.

### Bug Fixes
 - Fix missing status filters (active, pending, exited, withdrawal) for the `/eth/v1/beacon/states/{state_id}/validators` endpoint
 - Fixed issue which could lead to duplicate processing of some events when gossip is stopped and restarted.
 - Fixed issue which could cause sync committee duties to be calculated too early, potentially causing duties to be missed if the scheduling was changed by a reorg.