# Changelog

## Upcoming Breaking Changes
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`
- The `--p2p-discovery-site-local-addresses-enabled` option will be set to `false` by default. If you use the client's discovery inside the local network, update its launch command to toggle the option.
- The command argument `--Xengine-exchange-capabilities` will be removed, update to use `--engine-exchange-capabilities` if you are using this option.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Added the ability for the CL client to negotiate engine capabilities with the connected EL client. 
   This can be disabled by setting `--exchange-capabilities-enabled=false` if Shanghai is not supported on your EL client.
 - Enabled peer scoring by default. Can be disabled explicitly using `--Xp2p-gossip-scoring-enabled=false`
 - When failovers are configured, the validator client will perform a readiness check on startup to avoid retrieving validator statuses from a node which is not ready.

### Bug Fixes
