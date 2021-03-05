# Changelog

## Upcoming Breaking Changes
- Teku currently publishes a `head` event on the REST API 4 seconds into a slot even if a block has not been received. In a future release this will be changed so `head` event is only published when a new
  chain head block is imported. The `--Xvalidators-dependent-root-enabled` option can be used to switch to the new behaviour now for testing.
  Note: this should be applied to both the beacon node and validator client if running separately.
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes
- Binary downloads have been transitioned from Bintray to Cloudsmith.  Please ensure you use links in the documentation or release notes.
  Ansible users should ensure they have the latest version of the ansible role.
  
### Additions and Improvements
- Optimised fork choice to avoid unnecessary copying, reducing CPU and memory usage.
- Added a network definition for the prater test network `--network prater`.

### Bug Fixes
- Reduced verbosity of warning message when SIGHUP can't be interecepted (e.g. on Windows)
- Fixed build failure when checked out as a shallow clone. Shallow clones are still not recommended as the version number cannot be determined correctly.