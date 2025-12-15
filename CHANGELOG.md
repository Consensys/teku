# Changelog

## Upcoming Breaking Changes
 
## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

- Added new metrics `beacon_earliest_available_slot` and
  `data_column_sidecar_processing_validated_total`.
- Block proposal duties can now be scheduled in advance for fulu.
- Late block reorg enabled by default.
- Block building preparation enabled by default. The beacon node will now pre-compute head and pre-state selection in preparation for block building. (Disabled in Gnosis).
- Enabled a new version of sidecar recovery by default, which can be disabled with `--Xp2p-reworked-sidecar-recovery-enabled=false` if required.

### Bug Fixes
- Fixed a storage issue which sometimes caused Teku to crash during shut down.
- Fixed `peer_count` metric when using `--metrics-publish-endpoint` feature.
- Fixed `earliest_available_slot` calculation.
- Changed the default database version from leveldb2 to v6. Existing databases will not be changed, only new databases that are created.
