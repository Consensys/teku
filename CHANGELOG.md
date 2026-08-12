# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
 - Promoted `--Xvalidators-external-signer-concurrent-limit` to non-experimental `--validators-external-signer-concurrent-limit`, and impose maximum limit of 1024
 - Removed the `--deposit-snapshot-enabled` and `--Xdeposit-snapshot` CLI options along with the bundled deposit tree snapshots.

### Additions and Improvements
 - `--validator-keys` now accepts `<KEY_DIR>:<PASS_FILE>`, using a single password file for all keystores found in the directory.

### Bug Fixes
