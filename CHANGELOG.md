# Changelog

## Upcoming Breaking Changes
- Docker images will default to the JDK 17 variant in a future release.
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


### Additions and Improvements
- Support v.2.1.0 of the standard rest api. it should be noted that the 'version' has been changed to lower case to comply with the api specification.
- Attestations are now sent to the beacon node in batches by default when using the validator-client.
- Updated to Javalin 4 for the rest api.
- Added relevant epoch to attestation and sync committee performance log message.
- Removed ignore rule for aggregate attestation gossip where the attestation root has previously been seen.
- Added metrics to report client type of connected peers - `libp2p_connected_peers_current`, with client tag `Teku`, `Lighthouse`, `Prysm`, `Nimbus`, `Unknown`.
- Added support for Apple Silicon (M1 chips).
- Added LevelDB support for Linux/arm64.
- Switched executor queue size metrics to use labelled gauge.
- New console message when Teku switches forks. 
- Reduce CPU usage by using batching signature verification service for aggregate attestation and sync committee contributions.


### Bug Fixes
- Fixed issue where discovery did not correctly abort handshake attempts when a request timed out.
- Downgrade to jbslt 0.3.5 to resolve incompatibility with Windows 10.
- Fixed issue where `Syncing Completed` message was printing multiple times.
- Limited the number of validator public keys to lookup per request in `voluntary-exit` subcommand to avoid exceeding maximum URL length limits.
