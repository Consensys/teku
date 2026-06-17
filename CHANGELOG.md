# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

 * Dropped Windows support. Teku is no longer built, tested, or distributed for Windows. The `gradlew.bat` wrapper, Windows CI job, and Windows-specific native dependencies (LevelDB JNI) have been removed.

### Additions and Improvements

### Bug Fixes

- Prevent RPC rate-limited peers from immediately reconnecting inbound.
- Fixed missing `process_cpu_seconds_total` metric in the Docker images by adding the `jdk.management` module to the custom Java runtime.
