# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
 - Removed the legacy web3j-based Eth1/PoW deposit-log fetching. A node no longer requires an Eth1 JSON-RPC endpoint to run; deposits are sourced from the finalized deposit-tree snapshot and in-protocol (EIP-6110) execution requests. The following CLI options have been removed: `--eth1-endpoints` / `--eth1-endpoint`, `--eth1-deposit-contract-max-request-size`, `--Xeth1-missing-deposits-event-logging-enabled`, and `--Xdeposit-contract-logs-syncing-enabled`.

### Additions and Improvements

### Bug Fixes
 - Updated Libp2p to remove handshake info message.