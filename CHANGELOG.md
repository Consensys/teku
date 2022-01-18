
# Changelog

## Upcoming Breaking Changes
- Support for the Pyrmont testnet will be removed in an upcoming release. The Prater testnet should be used instead.
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The commandline option `--validators-performance-tracking-enabled` has been deprecated in favour of `--validators-performance-tracking-mode`
 
## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes
### Breaking Changes
* The "merge" hard fork has now been renamed to "bellatrix", matching the changes in the beacon spec 1.1.8 release.
    Custom network configurations will need to be updated and the `version` field in the Beacon REST API will now report `bellatrix` instead of `merge`.

### Additions and Improvements
* Optimised sync committee processing to avoid duplicate group checks for public keys.
* Reduced amount of data copying required when calculating sha256 hashes and use a more efficient sha256 implementation when available.
* Updated Javalin to version 4.2.0.
* Added periodic keep alive to event stream.

### Bug Fixes
* Rest api endpoints accepting validator IDs will no longer reject valid bytes48 hex strings that are not on the g2 curve.
* Upgraded discovery to fix `ConcurrentModificationException`.
* Fixed 503 response from REST APIs when creating an attestation or block based on finalized data.