# Changelog

## Upcoming Breaking Changes
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `-jdk14` and `-jdk15` docker image variants will be removed in a future release. JDK 14 and 15 are no longer receiving security updates from upstream vendors.
  Note that the default docker image usage JDK 16 and is still receiving security updates.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Additions and Improvements
 - Updated BLST library.


### Bug Fixes
 - Posting aggregates that fail validation to `/eth/v1/validator/aggregate_and_proofs` will now result in `SC_BAD_REQUEST` response, with details of the invalid aggregates in the response body.
 - Use atomic move when writing slashing protection records, if supported by the file system.
 - Increase the batch size when searching for unknown validator indexes from 10 to 50.
 - Fixed issue with the voluntary-exit subcommand and Altair networks which caused "Failed to retrieve network config" errors.
 - Fixed issue where redundant attestations were incorrectly included in blocks.
