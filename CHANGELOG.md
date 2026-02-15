# Changelog

## Upcoming Breaking Changes
 
## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

- Use jemalloc in our docker images to improve memory allocation
- Nodes with >50% custody requirements will be able to import blocks after downloading 50% of the sidecars, and the remaining sidecars will be handled as a background task. This includes nodes servicing validators in excess of 2048 eth effective balance, as well as voluntary supernodes. 

### Bug Fixes

- Changed get proposers api to be compatible with electra dependent roots.
