# Changelog

## Upcoming Breaking Changes

## Current Releases

For information on changes in released versions of Teku, see
the [releases page](https://github.com/Consensys/teku/releases).

## Unreleased Changes

### Breaking Changes

- The CLI options `--beacon-events-block-notify-when-validated-enabled` and
  `--beacon-events-block-notify-when-imported-enabled` have been removed. This change was made due
  to redundancy, as the functionality of these options is now covered by the new `block_gossip` SSE event.

### Additions and Improvements
- Improved compatibility with `/eth/v3/validator/blocks/{slot}` experimental beacon API for block production. It can now respond with blinded and unblinded content based on the block production flow. It also supports the `builder_boost_factor` parameter.

- Add `block_gossip` SSE event as per https://github.com/ethereum/beacon-APIs/pull/405

### Bug Fixes
