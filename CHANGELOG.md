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
- Improved compatibility with `/eth/v3/validator/blocks/{slot}` experimental beacon API for block production. It can now respond with blinded and unblinded content based on the block production flow. It also supports the `builder_boost_factor` parameter.
- Add `block_gossip` SSE event as per https://github.com/ethereum/beacon-APIs/pull/405
- Deposit tree snapshots will be downloaded from checkpoint-sync-url when available [#7715](https://github.com/Consensys/teku/issues/7715)
- Updated mainnet configuration with Deneb fork scheduled for epoch 269568 (March 13, 2024, 01:55:35pm UTC)
### Bug Fixes
