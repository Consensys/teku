# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

 * Teku now targets Java JDK 25 for builds and runtime.
 * Updated draft Gloas execution payload REST endpoints to match ethereum/beacon-APIs#613:
   `GET /eth/v1/beacon/execution_payload_envelope/{block_id}` is now
   `GET /eth/v1/beacon/execution_payload_envelopes/{block_id}`, and
   `GET /eth/v1/validator/execution_payload_bid/{slot}/{builder_index}` is now
   `GET /eth/v1/validator/execution_payload_bids/{slot}/{builder_index}`.

### Additions and Improvements

### Bug Fixes
 - Fixed a scenario where keys added via validator-api that rely on external signer are not slashing protected locally until the node is restarted. 
   To work around this issue, users should either keep slashing protection enabled on the external signer or restart the node after calling the add api. 
 - Fixed automatic detection of local node IPv6 address
 - Make sure sync committee duties are retried while the EL is optimistic.
 - Fixed a potential issue in importing blocks when data is not available.
 - Fixed potential NPE when SSE are not closed correctly.
 - Improved pruning for data column sidecars.
 - Delayed DVT attestation selection proof submissions until the target epoch starts, preventing lookahead duties from being submitted too early and ensuring stale pending batches are cancelled when duties are rescheduled.
