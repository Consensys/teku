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
- The `--initial-state` and `--eth1-deposit-contract-address` options has been removed from the `validator-client` subcommand. They have been ignored for some time but are now completely removed.

### Additions and Improvements
- Enables asynchronous database updates by default. This ensures slow disk access or LevelDB compactions don't cause delays in the beacon node.
- Make Validator Client connect to a failover event stream (if failovers are configured) when the current Beacon Node is not synced
- Detect Lodestar clients in `libp2p_connected_peers_current` metrics
- Reduce CPU and Memory consumption in shuffling, which will improve epoch transition performance
- Faster peer discovery on startup
- Increased leveldb open files to 1000 files by default. If teku fails to start, update maximum open files to 2048 or unlimited.

### Bug Fixes
- Resolves an issue with public key validation.
- Fix `/eth/v1/validator/register_validator` responding with a 400 status code and a misleading error message in case of exceptions
- Fix a `NullPointerException` for the gas limit when a proposer config is used and builder is enabled
- Update snakeyaml dependency to resolve cve-2022-25857 which could result in excessive memory usage when parsing YAML content
- Fixed an issue where the range requested for deposit logs was not reduced when using only `--ee-endpoint` leading to persistent timeouts with execution clients
