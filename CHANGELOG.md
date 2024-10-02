# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
- Renamed metrics `validator_attestation_publication_delay`,`validator_block_publication_delay` and `beacon_block_import_delay_counter` to include the suffix `_total` added by the current version of prometheus.

### Additions and Improvements
- Removed the deprecated [GetBlindedBlock](https://ethereum.github.io/beacon-APIs/#/ValidatorRequiredApi/produceBlindedBlock)
- Removed the deprecated [GetBlockV2](https://ethereum.github.io/beacon-APIs/?urls.primaryName=dev#/Validator/produceBlockV2)
- Implemented [PostAggregateAndProofsV2](https://ethereum.github.io/beacon-APIs/?urls.primaryName=dev#/Validator/publishAggregateAndProofsV2) (adding support for Electra)
- Added support for [Ephemery Testnet](https://github.com/ephemery.dev) `--network=ephemery`
- Updated bootnodes for Holesky network
- Disabled flood publish behaviour on all p2p subnets. `--Xp2p-flood-publish-enabled` experimental parameter can be used to re-enable it, restoring previous behaviour.
- Add a fix for [CVE-2024-7254](https://avd.aquasec.com/nvd/2024/cve-2024-7254/)
- Updated LUKSO configuration with Deneb fork scheduled for epoch 123075 (November 20, 2024, 16:20:00 UTC)
- Support for `IDONTWANT` libp2p protocol messages
- `/eth/v1/node/peers` endpoint now populates `enr` field of the peer whenever is possible

### Bug Fixes
 - Removed a warning from logs about non blinded blocks being requested (#8562)
 - Fixed `blockImportCompleted` log message timing [#8653](https://github.com/Consensys/teku/pull/8653)
