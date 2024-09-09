# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Removed the deprecated [GetBlindedBlock](https://ethereum.github.io/beacon-APIs/#/ValidatorRequiredApi/produceBlindedBlock)
- Removed the deprecated [GetBlockV2](https://ethereum.github.io/beacon-APIs/?urls.primaryName=dev#/Validator/produceBlockV2)
- Implemented [PostAggregateAndProofsV2](https://ethereum.github.io/beacon-APIs/?urls.primaryName=dev#/Validator/publishAggregateAndProofsV2) (adding support for Electra)
- Renamed validator metrics `attestation_publication_delay` and `block_publication_delay` to include the suffix `_total` added by the current version of prometheus.

### Bug Fixes
 - removed a warning from logs about non blinded blocks being requested (#8562)
