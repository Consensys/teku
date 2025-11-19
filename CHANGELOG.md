# Changelog

## Upcoming Breaking Changes
 
## Current Releases

## Unreleased Changes

### Breaking Changes
- jdk 24 docker image build has been removed in favour of jdk 25 docker image build.

### Additions and Improvements

### Bug Fixes
 - Fixed NPE in DasPreSampler (#10110).
 - Added connection direction to `beacon_peer_count` metric.
 - Added metrics for outgoing LibP2P RPC requests (`rpc_requests_total`, `rpc_requests_sent` and
  `rpc_requests_failed`)
