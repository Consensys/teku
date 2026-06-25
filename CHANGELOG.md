# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

 * Dropped Windows support. Teku is no longer built, tested, or distributed for Windows. The `gradlew.bat` wrapper, Windows CI job, and Windows-specific native dependencies (LevelDB JNI) have been removed.

### Additions and Improvements

- Added some extra fields to the peers output for beacon-apis #606. Consumers of the peers endpoint should be aware of this new optional data.
- QUIC enabled by default (uses UDP port 9001 by default)
- New Engine API client with better performance enabled by default
- Quic port command arguments `p2p-quic-port` (default 9001) and `p2p-quic-port-ipv6` (default 9091) were unhidden. Users will need to update firewall rules to allow incoming connections for QUIC (UDP).

### Bug Fixes

- Prevent RPC rate-limited peers from immediately reconnecting inbound.
- Fixed missing `process_cpu_seconds_total` metric in the Docker images by adding the `jdk.management` module to the custom Java runtime.
