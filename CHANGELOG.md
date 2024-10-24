# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
- Clean up old beacon states when switching from ARCHIVE to PRUNE or MINIMAL data storage mode
- Upgrade to jvm-libp2p 1.2.1 which brings:
  - message publishing over gossipsub improvements (addresses `Failed to publish * because no peers were available on the required gossip topic`)
  - IDONTWANT control message usage improvements

### Bug Fixes
 - Fixed a block production issue for Validator Client (24.10.0 to 24.10.2 teku VC), where required headers were not provided for JSON payloads. Default SSZ block production was unaffected.
 - Block production now uses json data (more like 24.8.0 did than 24.10) if the Eth-Consensus-version header is absent. 
