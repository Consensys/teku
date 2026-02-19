# Changelog

## Upcoming Breaking Changes
 
## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

- Use jemalloc in our docker images to improve memory allocation
- Nodes with >50% custody requirements will be able to import blocks after downloading 50% of the sidecars, and the remaining sidecars will be handled as a background task. This includes nodes servicing validators in excess of 2048 eth effective balance, as well as voluntary supernodes. 

### Bug Fixes

- fixed an issue with dependent root calculation which was causing future epoch block proposal duties to be recalculated.
- fixed v1 proposer duties returning incompatible roots for the v1 endpoint (should be compatible with pre-fulu dependent roots).