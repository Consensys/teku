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
- Dropped support for custom network configurations that do not use the current preset format. Presets have been in use since before the MainNet beaconchain launch so this is not expected to affect any users.

### Additions and Improvements
- Updated REST API to use Javalin 5
- Tightened validation of the fork for gossip messages
- Updated third party libraries.
- Support for the `/eth/v1/beacon/blinded_blocks/{block_id}` REST API

### Bug Fixes