# Changelog

## Upcoming Breaking Changes

- Next release will introduce a breaking change to Teku's metrics. This is due to some metrics changing names after a library upgrade.
We recommend all users of the `Teku - Detailed` dashboard to upgrade to version [Revision 12](https://grafana.com/api/dashboards/16737/revisions/12/download)  
as soon as possible. Documentation with all metrics that have been renamed will be provided.
- Next release will require Java 21. The current release is compatible, please consider upgrading before the next release.
- From the next release, you will need to explicitly set `--data-storage-mode=(prune|archive)` unless you're using minimal data-storage-mode (which is the default behaviour).

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

- Added metadata fields to `/eth/v1/beacon/blob_sidecars/{block_id}` Beacon API response as per https://github.com/ethereum/beacon-APIs/pull/441
- Added rest api endpoint `/teku/v1/beacon/state/finalized/slot/before/{slot}` to return most recent stored state at or before a specified slot.
- The validator client will start using the `v2` variant of the beacon node block publishing
  endpoints. In the cases where the block has been produced in the same beacon node, only equivocation validation will be done instead of the entire gossip validation.
- Docker images are now based on ubuntu 24.04 LTS (noble)
- The `teku vc` subcommand fails when no validator key source is provided. In order to run a validator client, one of the following options must be set:
  `--validator-keys`, `--validators-external-signer-url` or `--validator-api-enabled`
- Updated dependencies.

### Bug Fixes
- Fixed performance degradation introduced in 24.4.0 regarding archive state retrieval time.
- Fixed file writer when storing database mode settings to file (related to https://github.com/Consensys/teku/issues/8357).
