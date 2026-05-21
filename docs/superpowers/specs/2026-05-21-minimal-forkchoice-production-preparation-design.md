# Minimal Fork Choice Pinned Block Production Design

## Summary

Introduce a minimal pinned block-production record in `ForkChoiceNotifierImpl` so block production no longer depends on fallback recalculation in `internalGetPayloadId`.

The design assumes that `forkchoiceUpdated` with a requested block production slot is always called before `getPayloadId` for that block. Under that invariant, `getPayloadId` should consume the pinned block-production record or fail clearly if the record is missing or expired.

This design intentionally avoids broader lifecycle redesigns. It only adds the state needed to make production payload attributes awaitable and parent/slot scoped.

## Goals

- Remove the fallback recalculation attempt from `internalGetPayloadId`.
- Make block-production payload attributes awaitable when `getPayloadId` arrives before async calculation completes.
- Keep production attributes tied to the pinned block-production head selected for block production.
- Restore late-block reorg suppression via `shouldOverrideForkChoiceUpdate`.
- Calculate payload attributes from the same head used by the FCU being sent.
- Keep `getStateInEpoch` for proposer-connectivity checks only.

## Non-Goals

- Do not implement a general payload-attributes lifecycle redesign.
- Do not add merge/terminal-block recovery behavior beyond the current practical post-merge path.
- Do not preserve recovery for extremely slow block production after the pinned block-production record has expired.
- Do not address Gloas-specific withdrawals or remote-proposer bid behavior in this change.

## Pinned Block-Production Record

Replace `lastProposingSlot` with a small record that remains stable between `internalForkChoiceUpdated(forkChoiceState, requestedBlockProductionSlot = N)` and the corresponding `getPayloadId(parent, N)`:

```java
record PinnedBlockProductionPreparation(
    UInt64 slot,
    ForkChoiceNode parentBeaconBlock,
    ForkChoiceUpdateData forkChoiceUpdateData,
    SafeFuture<Optional<ExecutionPayloadContext>> executionPayloadContext) {}
```

The record owns the stable future consumed by `getPayloadId`. `ForkChoiceUpdateData` remains an EL-call snapshot: when payload attributes resolve, the pinned record is updated with a new `ForkChoiceUpdateData` containing those attributes, and the sent FCU's execution payload context is propagated into the record's future. The ordinary `forkChoiceUpdateData` field remains the non-production FCU state and is not the source of truth while a matching pinned block-production record is active.

The future represents the complete production path needed by `getPayloadId`: payload attributes calculated, FCU sent, and EL payload context available.

The pinned record is not cleared when `getPayloadId` consumes it. A validator client request retry after a production failure can trigger another `getPayloadId(parent, N)` for the same slot, and the same pinned record should still be available. The record is cleared only by success or abandonment signals:

- success: fork choice head advances to the pinned slot or later, which means the locally produced block was imported or superseded by a later head.
- abandonment: attestations are due for the pinned slot or later, which means the block production flow did not complete in the intended slot window.

## Head Update Path

For `internalForkChoiceUpdated(forkChoiceState, requestedBlockProductionSlot = empty)`:

1. If the head slot is greater than or equal to the pinned slot, clear the pinned record.
2. If late-block reorg is enabled and `recentChainData.shouldOverrideForkChoiceUpdate(...)` says the ordinary head should be overridden, skip replacing/sending the ordinary FCU.
3. Otherwise replace ordinary `forkChoiceUpdateData` with the new head.
4. Calculate payload attributes for the current or next proposal slot from that same FCU head.
5. Send the ordinary FCU when payload attributes are available.

This keeps ordinary head advancement separate from pinned block production.

## Attestations-Due Path

For `onAttestationsDue(slot)`:

1. Clear the pinned block-production record when it belongs to `slot <= attestationDueSlot`.
2. Prepare payload attributes for `slot + 1` using the current ordinary FCU head.
3. Send the ordinary FCU when payload attributes are available.

If block production is so late that its pinned record has already expired, `getPayloadId` is allowed to fail instead of attempting fallback recalculation.

## Production Path

For `internalForkChoiceUpdated(forkChoiceState, requestedBlockProductionSlot = N)`:

1. Use the pinned block-production head in `forkChoiceState`.
2. Create or replace `PinnedBlockProductionPreparation` for slot `N` and that parent.
3. Calculate payload attributes for slot `N` from the pinned block-production head state.
4. Send the production FCU.
5. Propagate the production FCU execution payload context into the pinned record future.

This guarantees randao, withdrawals, parent, and payload ID are all derived from the same production head.

## Payload ID Path

For `getPayloadId(parent, N)`:

1. Require a matching `PinnedBlockProductionPreparation` for parent and slot `N`.
2. Wait on its stable `executionPayloadContext`.
3. Fail clearly if there is no matching pinned record, the pinned record has expired, or the EL does not return a payload context.
4. Leave the pinned record in place after a successful match so same-slot retry can reuse it.
5. Do not fallback to a fresh FCU recalculation.

This turns missing pinned block-production state into an invariant violation instead of hidden recovery logic.

## Payload Attribute State Selection

`ProposersDataManager.calculatePayloadBuildingAttributes` must retrieve state from the head in the `ForkChoiceUpdateData` being prepared.

`getStateInEpoch` remains for proposer-connectivity checks. A separate helper should be used for payload-attribute state retrieval so ordinary and production FCUs both derive attributes from their own head.

## Testing

Add or update unit tests for:

- `getPayloadId` waits for pending production payload attributes and resolves through the pinned record future.
- `getPayloadId` fails when no matching pinned block-production record exists.
- Pinned block production calculates payload attributes from the pinned block-production head, not a later chain head.
- Non-production FCU updates are skipped when late-block reorg override is active.
- Payload attribute calculation retrieves state from the FCU head.
- `getPayloadId` does not clear the pinned record, allowing same-slot retry after production failure.
- Head advancement clears the pinned record as the success signal.
- Attestations due clears the expired pinned block-production record and prepares `slot + 1`.

Run:

```bash
./gradlew ethereum:statetransition:spotlessJavaCheck
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ProposersDataManagerTest
```
