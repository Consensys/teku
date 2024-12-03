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
- improve block publishing performance, especially relevant with locally produced blocks
- delay blobs publishing until the block is published to at least 1 peer, especially relevant with locally produced blocks with low upload bandwidth connections. Can be disabled via `--Xp2p-gossip-blobs-after-block-enabled=false`

### Bug Fixes
- Added a startup script for unix systems to ensure that when jemalloc is installed the script sets the LD_PRELOAD environment variable to the use the jemalloc library
