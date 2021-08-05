# Changelog

## Upcoming Breaking Changes
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Scheduled Altair upgrade on the Pyrmont testnet at epoch 61650.
- Updated default docker image to Java 16 using AdoptOpenJDK Ubuntu based images.
- Docker images now include `curl` to support adding health checks.
- Rest Api's for Altair have been moved out of 'experimental', and are now stable.
  - implement POST `/eth/v1/beacon/pool/sync_committees` to allow validators to submit sync committee signatures to the beacon node.
  - implement POST `/eth/v1/validator/duties/sync/{epoch}` for Altair fork.
  - implement GET and POST `/eth/v1/validator/sync_committee_subscriptions` for Altair fork.
  - implement GET `/eth/v2/validator/blocks/{slot}` for Altair fork.
  - implement GET `/eth/v2/debug/beacon/states/:state_id` for Altair fork.
  - implement GET `/eth/v1/beacon/states/{state_id}/sync_committees` for Altair fork.
  - `/eth/v1/validator/blocks/{slot}` will now produce an altair block if an altair slot is requested.
  - `/eth/v1/node/identity` will now include syncnets in metadata
  - implement event channel for `/eth/v1/events&topic=contribution_and_proof` for Altair fork.
- Rest Api's that are not supported in Altair have been marked as deprecated.

### Bug Fixes
- Fixed `ConcurrentModificationException` and `NoSuchElementException` in validator performance reporting.
- Upgraded the discovery library, providing better memory management and standards compliance.
- Fixed `InvalidDepositEventsException` error after restart.
- Improve compatibility with Lighthouse bootnodes by not including the local address in the local ENR until the external address has been determined.

### Experimental: New Altair REST APIs
- implement POST `/eth/v1/beacon/pool/sync_committees` to allow validators to submit sync committee signatures to the beacon node.
- implement POST `/eth/v1/validator/duties/sync/{epoch}` for Altair fork.
- implement GET and POST `/eth/v1/validator/sync_committee_subscriptions` for Altair fork.
- implement GET `/eth/v2/validator/blocks/{slot}` for Altair fork.
- implement GET `/eth/v2/debug/beacon/states/:state_id` for Altair fork.
- implement GET `/eth/v1/beacon/states/{state_id}/sync_committees` for Altair fork.
- `/eth/v1/validator/blocks/{slot}` will now produce an altair block if an altair slot is requested.
- `/eth/v1/node/identity` will now include syncnets in metadata
- implement event channel for `/eth/v1/events&topic=contribution_and_proof` for Altair fork.
