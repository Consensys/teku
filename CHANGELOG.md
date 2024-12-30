# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
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
- Optimized blobs validation pipeline
- Remove delay when fetching blobs from the local EL on block arrival

### Bug Fixes