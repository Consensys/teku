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
### Breaking Changes
- Removed migration code from the old `ProtoArraySnapshot` based storage to the new format.
   Any node correctly following the Altair chain has already gone through this migration so no users should be affected by this.

### Additions and Improvements
- Introduces a new database format for archive nodes that significantly improves response times for queries that require historic state data.
    Existing databases and nodes using the default PRUNE storage mode are unchanged. Archive nodes wishing to take advantage of this will need to perform a full resync.
- Docker images are now published with multi-arch support including Linux/amd64 and Linux/arm64 
- The default docker image now uses JDK 17 instead of 16. The JDK 16 image is still available with the version suffix `-jdk16`
- Include the date in output to console, when log files are not being written.
- Reinstated the ignore rule for aggregate attestation gossip where the attestation root has been previously seen.
- Added new metrics `executor_signature_verifications_queue_size`, `executor_signature_verifications_task_count`, 
    `executor_signature_verifications_batch_count` and `executor_signature_verifications_batch_size` to give visibility 
    into the remaining capacity of the signature verification process.
- Added support for using the optimized BLST which is more efficient but does not support some older CPUs. 
    On Linux and Mac Teku will attempt to detect if the CPU is compatible and automatically use the optimized version.
    On Windows or if auto-detection fails the portable version continues to be used.
    The version of BLST to use can be explicitly set by setting the `teku.portableBlst` system property. e.g `JAVA_OPTS="-Dteku.portableBlst=true" teku`

### Bug Fixes