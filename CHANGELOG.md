# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes
- Removed `epoch` from the POST `/eth/v1/validator/liveness/{epoch}` response as it was not part of the api spec.

### Additions and Improvements
- The latest version of [blst](https://github.com/supranational/blst) will automatically use optimized code paths if they are supported. As a result, `JAVA_OPTS="-Dteku.portableBlst=true"` is no longer necessary for some older systems.
- The voluntary exit subcommand now accepts `--network=<NETWORK>` command line option, using it to load the network specification rather than loading configuration from the rest api, if specified. 
- Add `/teku/v1/beacon/blob_sidecars/{slot}` Teku API which returns all blob sidecars (canonical and non-canonical) at a specific slot
- Updated LevelDb native library which is using LevelDb 1.23 using latest Snappy to 1.1.10 for compression (this change doesn't apply to Windows)

### Bug Fixes
- When the rest-api's fail to start up they can now potentially 'fail fast' rather than silently ignoring the issue.