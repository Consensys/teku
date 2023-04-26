# Changelog

## Upcoming Breaking Changes

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Locally submitted exits and bls changes will now periodically broadcast if they are not actioned, to address operations being lost in remote pools.
 - Set `User-Agent` header to "teku/v<version>" (e.g. teku/v23.4.0) when making builder bid requests to help builders identify clients and versions. Use `--builder-set-user-agent-header=false` to disable. 

### Bug Fixes
