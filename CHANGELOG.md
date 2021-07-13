# Changelog

## Upcoming Breaking Changes
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- Teku no longer publishes a `head` event on the REST API 4 seconds into a slot if a block has not been received. 
  The `--Xvalidators-dependent-root-enabled=false` option can be used to restore the old behaviour.
  Note: this should be applied to both the beacon node and validator client if running separately.

### Additions and Improvements
- Added `migrate-data` subcommand to allow users to migrate to a leveldb relatively easily without requiring a full re-sync.
- Improved compatibility of the validator client with other beacon nodes using the REST API by using 
  the `dependent-root` field to detect re-orgs instead of depending on the beacon chain publishing re-org events when empty slots are later filled.

### Bug Fixes
- Fix an issue where new peers would not be found after a network outage.
- Fix a file handle leak in jvm-libp2p.
- Fix `previous_epoch_participation` and `current_epoch_participation` representation in 
  `/eth/v2/debug/beacon/states/:state_id` so that it comes back as an array rather than a byte string.
- `/eth/v1/beacon/pool/sync_committees` incorrectly returned 503 when there were no errors instead of 200.

### Experimental: New Altair REST APIs
- implement POST `/eth/v1/beacon/pool/sync_committees` to allow validators to submit sync committee signatures to the beacon node.
- implement POST `/eth/v1/validator/duties/sync/{epoch}` for Altair fork.
- implement GET and POST `/eth/v1/validator/sync_committee_subscriptions` for Altair fork.
- implement GET `/eth/v2/validator/blocks/{slot}` for Altair fork.
- implement GET `/eth/v2/debug/beacon/states/:state_id` for Altair fork.
- implement GET `/eth/v1/beacon/states/{state_id}/sync_committees` for Altair fork.
- `/eth/v1/validator/blocks/{slot}` will now produce an altair block if an altair slot is requested.
- `/eth/v1/node/identity` will now include syncnets in metadata
