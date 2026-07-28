# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
 - Removed the legacy web3j-based Eth1/PoW deposit-log fetching. A node no longer requires an Eth1 JSON-RPC endpoint to run; deposits are sourced from the finalized deposit-tree snapshot and in-protocol (EIP-6110) execution requests. The following CLI options have been removed: `--eth1-endpoints` / `--eth1-endpoint`, `--eth1-deposit-contract-max-request-size`.
 - Removed the non-production `validator-tools send-deposits` and `validator-tools generate-and-send-deposits` internal subcommands (web3j-based deposit submission). `validator-tools generate-keys` is unaffected.
 - Removed the GetDepositSnapshot RPC endpoint, which has been deprecated and removed since v3.0.0 of the Beacon API spec.

### Additions and Improvements

### Bug Fixes
 - Fixed Beacon REST API socket retention when clients cancel pending asynchronous requests. Requests now time out after 30 seconds.
