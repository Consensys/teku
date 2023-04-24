# Changelog

## Upcoming Breaking Changes
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`
- The command argument `--Xengine-exchange-capabilities` will be removed, update to use `--engine-exchange-capabilities` if you are using this option.
- The command argument `--Xdeposit-snapshot-enabled` will be removed, just remove it from commandline/configuration if you use it, updated argument `--deposit-snapshot-enabled` defaults to true now.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- The `--p2p-discovery-site-local-addresses-enabled` option is set to `false` by default. If you use the client's discovery inside the local network, update its launch command to toggle the option.

### Additions and Improvements
 - Added the ability for the CL client to negotiate engine capabilities with the connected EL client. 
   This can be disabled by setting `--exchange-capabilities-enabled=false` if Shanghai is not supported on your EL client.
 - Enabled peer scoring by default. Can be disabled explicitly using `--Xp2p-gossip-scoring-enabled=false`
 - When failovers are configured, the validator client will perform a readiness check on startup to avoid retrieving validator statuses from a node which is not ready.
 - Enabled deposit tree snapshot bundles for major networks and persists it after finalization to decrease EL pressure and speed up node startup. Use `--deposit-snapshot-enabled=false` to disable.
 - Optimized validator exit processing during state transition, to speed up block import containing multiple validator exits.
 - Locally submitted exits and bls changes will now periodically broadcast if they are not actioned, to address operations being lost in remote pools.
 - Set `User-Agent` header to "teku/v<version>" (e.g. teku/v23.4.0) when making builder bid requests to help builders identify clients and versions. Use `--builder-set-user-agent-header=false` to disable. 

### Bug Fixes
