# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
 - Removed the legacy web3j-based Eth1/PoW deposit-log fetching. A node no longer requires an Eth1 JSON-RPC endpoint to run; deposits are sourced from the finalized deposit-tree snapshot and in-protocol (EIP-6110) execution requests. The following CLI options have been removed: `--eth1-endpoints` / `--eth1-endpoint`, `--eth1-deposit-contract-max-request-size`.
 - Removed the non-production `validator-tools send-deposits` and `validator-tools generate-and-send-deposits` internal subcommands (web3j-based deposit submission). `validator-tools generate-keys` is unaffected.

### Additions and Improvements
 - Added ssz output for validator balances api.

### Bug Fixes
 - Updated Libp2p to remove handshake info message.
 - Dual-stack P2P nodes can now bind IPv4 and IPv6 listeners to the same port.
