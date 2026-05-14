# Changelog

## Upcoming Breaking Changes

 * Teku will be moving to Java JDK 25 in a future release.

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

### Bug Fixes
 - Fixed a scenario where keys added via validator-api that rely on external signer are not slashing protected locally until the node is restarted. 
   To work around this issue, users should either keep slashing protection enabled on the external signer or restart the node after calling the add api. 
 - Fixed automatic detection of local node IPv6 address
 - Fixed a potential issue in importing blocks when data is not available.
 - Fixed potential NPE when SSE are not closed correctly.