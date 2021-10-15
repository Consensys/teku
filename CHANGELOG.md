# Changelog

## Upcoming Breaking Changes
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The commandline option `--validators-performance-tracking-enabled` has been deprecated in favour of `--validators-performance-tracking-mode`
- The commandline option `--network` of the `validator-client` subcommand introduces a new option value `auto`, which automatically 
fetches network configuration information from the configured beacon node endpoint. Other `--network` option values for an external validator client 
 are now deprecated in favour of this option value (usage: `teku validator-client --network=auto`)
 
## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- The `-jdk14` and `-jdk15` docker image variants are no longer being updated as JDK 14 and 15 are no longer receiving security updates from upstream vendors.
  Note that the default docker image usage JDK 16 and is still receiving security updates.

### Additions and Improvements
- Added `validator_local_validator_counts` metric to report number of local validators by current status.
- Added JDK 17 docker images. The JDK 16 based images remain the default, append `-jdk17` to the docker image version to use the JDK 17 variant. 
- Upgraded dependencies `io.netty`, `okhttp` and `io.vertex`.
- Upgraded to BLST 0.3.6.
- Added new `--p2p-udp-port` and `--p2p-advertised-udp-port` options to support using different ports for TCP and UDP.
- Added an additional bootnode for the Prater testnet.


### Bug Fixes
 - Fixed a possible crash on shutdown when using levelDb.
 - Set an idle timeout for metrics connections, to clean up ports when no longer used.
 - Fixed error when building from a source download rather than a git checkout.
   Now logs a warning when building without git information to warn users that version information will not be available.
 - Fixed an issue where discovery did not recover if it was initially started while the bootnodes were unavailable.