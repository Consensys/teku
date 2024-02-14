# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

- The CLI options `--beacon-events-block-notify-when-validated-enabled` and
  `--beacon-events-block-notify-when-imported-enabled` have been removed. This change was made due
  to redundancy, as the functionality of these options is now covered by the new `block_gossip` and
  the existing `block` SSE events.

### Additions and Improvements

- Updated Mainnet configuration with Deneb fork scheduled for epoch 269568 (March 13, 2024, 13:55:35 UTC)
- Updated Gnosis configuration with Deneb fork scheduled for epoch 889856 (March 11, 2024, 18:30:20 UTC)
- Improved compatibility with `/eth/v3/validator/blocks/{slot}` experimental beacon API for block production. It can now respond with blinded and unblinded content based on the block production flow. It also supports the `builder_boost_factor` parameter.
- Add `block_gossip` SSE event as per https://github.com/ethereum/beacon-APIs/pull/405
- Deposit tree snapshots will be downloaded from checkpoint-sync-url when available [#7715](https://github.com/Consensys/teku/issues/7715)
- Applied fork-choice confirmation rule prerequisite change outlined in https://github.com/ethereum/consensus-specs/pull/3431.

### Bug Fixes
