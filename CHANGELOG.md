# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
- `--logging` CLI option has been modified to specify only the logging level of Teku classes and
  related dependencies (discovery, lipb2p). Logging level of other 3rd party dependencies can still be
  changed either
  via [log4j configuration file](https://docs.teku.consensys.io/how-to/monitor/configure-logging#advanced-custom-logging)
  or via [API](https://consensys.github.io/teku/#tag/Teku/operation/putLogLevel) at runtime.

### Additions and Improvements
 - Added beacon-api `/eth/v1/beacon/states/{state_id}/pending_partial_withdrawals` endpoint for use post-electra.
 - Added beacon-api `/eth/v1/beacon/states/{state_id}/pending_deposits` endpoint for use post-electra.
 - Added beacon-api `/eth/v1/beacon/states/{state_id}/pending_consolidations` endpoint for use post-electra.
 - Added Chiado Electra configuration due at Mar-06-2025 09:43:40 GMT+0000
 - Removed stack trace by default from duty failure messages.
 - Holesky pectra bad block ignored to aid syncing
 - Added a development flag to increase the maximum pending queue for attestations.
 - Added support for [Hoodi testnet](https://github.com/eth-clients/hoodi/).

### Bug Fixes
 - Added 415 response code for beacon-api `/eth/v1/validator/register_validator`.
 - Accept HTTP headers in a case-insensitive manner ([RFC 7230](https://datatracker.ietf.org/doc/html/rfc7230#section-3.2))
 - Increased the attestation queue size limits.