# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
 - Promoted `--Xvalidators-external-signer-concurrent-limit` to non-experimental `--validators-external-signer-concurrent-limit`, and imposed maximum limit of 1024
 - Removed the `--deposit-snapshot-enabled` and `--Xdeposit-snapshot` CLI options along with the bundled deposit tree snapshots.

### Additions and Improvements
 - Increased the default value of `--p2p-target-subnet-subscriber-count` from 2 to 3.
 - Added native support for the [Plataberget testnet](https://plataberget.dev/). Use `--network=plataberget` to join the network.
 - `--validator-keys` now accepts `<KEY_DIR>:<PASS_FILE>`, using a single password file for all keystores found in the directory.
 - Improved debug/beacon/states endpoint to allow searching of the finalized state root, to assist third party products searching on roots.

### Bug Fixes
 - Fixed `data_column_sidecar` gossip decoding to use the schema of the topic's fork instead of the highest supported milestone. Previously, on networks with Gloas scheduled, every Fulu-era column sidecar received via gossip failed deserialization.
 - Fixed a regression where archive nodes using `leveldb-tree` storage would take an extremely long time to start up.
 - Post-Electra, the `committee_index` query parameter in `GET /eth/v1/validator/attestation_data` is now ignored instead of rejected when non-zero, matching the behaviour of other consensus clients.
 - Trigger an immediate peer search when publishing sync committee messages fails because there are no peers available on the required gossip topic.
