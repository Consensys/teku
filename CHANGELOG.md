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
- The commandline option `--network` of the `validator-client` subcommand has been undeprecated and can be used to select a network for standalone validator clients. When set to `auto`, it automatically
  fetches network configuration information from the configured beacon node endpoint.  

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Improve Execution Layer error logging
- Add new validator client metric `validator_remote_beacon_nodes_requests_total` which tracks the requests made to remote beacon nodes (useful when there are failovers configured)
- The `voluntary-exit` subcommand can restrict the exit to a specific list of validators public keys using the option `--validator-public-keys`.
  Example: `teku voluntary-exit --beacon-node-api-endpoint=<ENDPOINT>[,<ENDPOINT>...]... --data-validator-path=<PATH> --include-keymanager-keys=<BOOLEAN> --validator-keys=<KEY_DIR>:<PASS_DIR> | <KEY_FILE>:<PASS_FILE> --validator-public-keys=<PUBKEY>[,<PUBKEY>...]...`
  To include validator keys managed via keymanager APIs, the option `--include-keymanager-keys` could be set to `true` (The default value is set to `false`)
- Throttle signing of validator registrations when using an external signer

### Bug Fixes
- Filter out unknown validators when sending validator registrations to the builder network
- Fix issue which could cause locally produced aggregates to not be gossiped
- Fix issue where the sync module could cause `Unexpected rejected execution due to full task queue in nioEventLoopGroup` log messages and high CPU usage
- Fix issue where /readiness endpoint returned 200 when Execution Client was not available.