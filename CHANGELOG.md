# Changelog

## Released Changes
### Breaking Changes
- The commandline option `--network` of the `validator-client` subcommand introduces a new option value `auto`, which automatically
  fetches network configuration information from the configured beacon node endpoint. Other `--network` option values for an external validator client
  are now deprecated in favour of this option value (usage: `teku validator-client --network=auto`).
- The default value for the `--network` commandline option of the `validator-client` command has changed from `mainnet` to `auto`.

## Upcoming Breaking Changes
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The commandline option `--validators-performance-tracking-enabled` has been deprecated in favour of `--validators-performance-tracking-mode`
 
## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes
### Additions and Improvements
- Introduces a new database format for archive nodes that significantly improves response times for queries that require historic state data.
    Existing databases and nodes using the default PRUNE storage mode are unchanged. Archive nodes wishing to take advantage of this will need to perform a full resync.
- Docker images are now published with multi-arch support including Linux/amd64 and Linux/arm64 
- The default docker image now uses JDK 17 instead of 16. The JDK 16 image is still available with the version suffix `-jdk16`

### Bug Fixes