# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
- `--Xvalidators-builder-registration-default-gas-limit` CLI option is replaced by `--validators-builder-registration-default-gas-limit`
- `--Xp2p-sync-rate-limit` CLI option is removed in favour of `--Xp2p-sync-blocks-rate-limit` and `--Xp2p-sync-blob-sidecars-rate-limit`
- `--Xpeer-rate-limit` CLI options is removed in favour of `--Xpeer-blocks-rate-limit` and `--Xpeer-blob-sidecars-rate-limit`
- With the upgrade of the Prometheus Java Metrics library, there are the following changes:
  - Gauge names are not allowed to end with `total`, therefore metrics as `beacon_proposers_data_total` and `beacon_eth1_current_period_votes_total` are dropping the `_total` suffix
  - The `_created` timestamps are not returned by default.
  - Some JVM metrics have changed name to adhere to the OTEL standard (see the table below), [Teku Detailed Dashboard](https://grafana.com/grafana/dashboards/16737-teku-detailed/) is updated to support both names

    | Old Name                        | New Name                        |
    |---------------------------------|---------------------------------|
    | jvm_memory_bytes_committed      | jvm_memory_committed_bytes      |
    | jvm_memory_bytes_init           | jvm_memory_init_bytes           |
    | jvm_memory_bytes_max            | jvm_memory_max_bytes            |
    | jvm_memory_bytes_used           | jvm_memory_used_bytes           |
    | jvm_memory_pool_bytes_committed | jvm_memory_pool_committed_bytes |
    | jvm_memory_pool_bytes_init      | jvm_memory_pool_init_bytes      |
    | jvm_memory_pool_bytes_max       | jvm_memory_pool_max_bytes       |
    | jvm_memory_pool_bytes_used      | jvm_memory_pool_used_bytes      |


### Additions and Improvements
- Default the gas limit to 36 million for externally produced blocks
- Optimized blobs validation pipeline
- Remove delay when fetching blobs from the local EL on block arrival
- New validator metric `validator_next_attestation_slot` to highlight the next slot that a validator is expected to publish an attestation [#8795](https://github.com/Consensys/teku/issues/8795)
- Support for SSZ format in builder API (mev-boost)
- Added the expected gas limit to the 'not honouring the validator gas limit preference' warning message.

### Bug Fixes
- Fix `--version` command output [#8960](https://github.com/Consensys/teku/issues/8960)
- Fix issue (introduced in `24.12.1`) with peer stability when the upperbound is set to a high number