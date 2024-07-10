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
- Added a state pruner that can limit the number of finalized states stored when running an archive node.

### Bug Fixes
