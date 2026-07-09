# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Added ssz output for validator balances api.
 - Updated Gloas config and preset values to track consensus-specs `v1.7.0-alpha.12`: `MIN_BUILDER_WITHDRAWABILITY_DELAY` (mainnet) 8192 -> 64, `PAYLOAD_DUE_BPS` 7500 -> 5000, `MAX_BUILDER_DEPOSIT_REQUESTS_PER_PAYLOAD` 256 -> 64, and `BUILDER_WITHDRAWAL_PREFIX` 0x03 -> 0xB0.

### Bug Fixes
 - Updated Libp2p to remove handshake info message.
 - Dual-stack P2P nodes can now bind IPv4 and IPv6 listeners to the same port.
