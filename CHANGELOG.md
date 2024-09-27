# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Removed the deprecated [GetBlindedBlock](https://ethereum.github.io/beacon-APIs/#/ValidatorRequiredApi/produceBlindedBlock)
- Removed the deprecated [GetBlockV2](https://ethereum.github.io/beacon-APIs/?urls.primaryName=dev#/Validator/produceBlockV2)
- Implemented [PostAggregateAndProofsV2](https://ethereum.github.io/beacon-APIs/?urls.primaryName=dev#/Validator/publishAggregateAndProofsV2) (adding support for Electra)
- Added support for [Ephemery Testnet](https://github.com/ephemery.dev) `--network=ephemery`
- Renamed metrics `validator_attestation_publication_delay`,`validator_block_publication_delay` and `beacon_block_import_delay_counter` to include the suffix `_total` added by the current version of prometheus.
- Updated bootnodes for Holesky network
- Added new `--p2p-flood-publish-enabled` parameter to control whenever flood publishing behaviour is enabled (applies to all subnets). Previous teku versions always had this behaviour enabled. Default is `true`.
- Add a fix for [CVE-2024-7254](https://avd.aquasec.com/nvd/2024/cve-2024-7254/)

### Bug Fixes
 - removed a warning from logs about non blinded blocks being requested (#8562)
