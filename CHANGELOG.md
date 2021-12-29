# Changelog

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
* API users should switch to using response 'code' from BadRequests, rather than 'status' field, in line with the standard API.

### Additions and Improvements
* Added `kintsugi` network definition. 
* Optimised discv5 by caching calculated node ID.
* Avoided object allocation when calculating integer square root values.

### Bug Fixes
* Updated to log4j 2.17.1.
* Made BadRequests compliant with the api, returning 'code' rather than 'status'.
* Fixed: Invalid sync contributions were created if a validator was present multiple times in the same sync sub-committee.
* Reduced error to warning when sync contribution cannot be created because the beacon node has no matching sync messages.
* Fixed issue where validator duties were not performed during the first epoch after startup.
* Updated jvm-libp2p to improve negotiation of mplex and multistream connections.
