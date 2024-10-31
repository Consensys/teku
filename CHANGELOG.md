# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

### Bug Fixes
- Added a startup script for unix systems to ensure that when jemalloc is installed the script sets the LD_PRELOAD environment variable to the use the jemalloc library

- Fixed a checkpoint sync issue where Teku couldn't start when the finalized state has been transitioned with empty slot(s) 
