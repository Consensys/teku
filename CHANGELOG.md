# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

 * Teku now targets Java JDK 25 for builds and runtime.

### Additions and Improvements

### Bug Fixes
 - Fixed a scenario where keys added via validator-api that rely on external signer are not slashing protected locally until the node is restarted. 
   To work around this issue, users should either keep slashing protection enabled on the external signer or restart the node after calling the add api. 
 - Fixed automatic detection of local node IPv6 address
 - Fixed a potential issue in importing blocks when data is not available.
 - Fixed potential NPE when SSE are not closed correctly.