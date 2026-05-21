# Minimal Fork Choice Production Preparation Design

## Summary

Introduce a minimal production-preparation state in `ForkChoiceNotifierImpl` so block production no longer depends on fallback recalculation in `internalGetPayloadId`.

The design assumes that `forkchoiceUpdated` with a proposing slot is always called before `getPayloadId` for that block. Under that invariant, `getPayloadId` should consume the already-prepared production state or fail clearly if the preparation is missing or expired.

This design intentionally avoids the broader session redesign. It only adds the state needed to make production payload attributes awaitable and parent/slot scoped.

## Goals

- Remove the fallback recalculation attempt from `internalGetPayloadId`.
- Make block-production payload attributes awaitable when `getPayloadId` arrives before async calculation completes.
- Keep production attributes tied to the pinned/proposer head selected for block production.
- Restore late-block reorg suppression via `shouldOverrideForkChoiceUpdate`.
- Calculate payload attributes from the same head used by the FCU being sent.
- Keep `getStateInEpoch` for proposer-connectivity checks only.

## Non-Goals

- Do not implement the full payload-attributes session redesign.
- Do not add merge/terminal-block recovery behavior beyond the current practical post-merge path.
- Do not preserve recovery for extremely slow block production after the production preparation has expired.
- Do not address Gloas-specific withdrawals or remote-proposer bid behavior in this change.

## Production Preparation State

Replace `lastProposingSlot` with a small record that represents the active production preparation:

```java
record BlockProductionPreparation(
    UInt64 slot,
    ForkChoiceNode parentBeaconBlock,
    ForkChoiceUpdateData forkChoiceUpdateData,
    SafeFuture<Optional<ExecutionPayloadContext>> executionPayloadContext) {}
```

The record owns the stable future consumed by `getPayloadId`. `ForkChoiceUpdateData` remains an EL-call snapshot: when payload attributes resolve, the production preparation is updated with a new `ForkChoiceUpdateData` containing those attributes, and the sent FCU's execution payload context is propagated into the preparation future. The ordinary `forkChoiceUpdateData` field remains the non-production FCU state and is not the source of truth for an active production preparation.

The future represents the complete production path needed by `getPayloadId`: payload attributes calculated, FCU sent, and EL payload context available.

## Head Update Path

For `internalForkChoiceUpdated(forkChoiceState, proposingSlot = empty)`:

1. If late-block reorg is enabled and `recentChainData.shouldOverrideForkChoiceUpdate(...)` says the ordinary head should be overridden, skip replacing/sending the ordinary FCU.
2. Otherwise replace ordinary `forkChoiceUpdateData` with the new head.
3. Calculate payload attributes for the current or next proposal slot from that same FCU head.
4. Send the ordinary FCU when payload attributes are available.

This keeps ordinary head advancement separate from production preparation.

## Attestations-Due Path

For `onAttestationsDue(slot)`:

1. Clear expired production preparation when it belongs to `slot <= attestationDueSlot`.
2. Prepare payload attributes for `slot + 1` using the current ordinary FCU head.
3. Send the ordinary FCU when payload attributes are available.

If block production is so late that its preparation has already expired, `getPayloadId` is allowed to fail instead of attempting fallback recalculation.

## Production Path

For `internalForkChoiceUpdated(forkChoiceState, proposingSlot = N)`:

1. Use the pinned/proposer head in `forkChoiceState`.
2. Create or replace `BlockProductionPreparation` for slot `N` and that parent.
3. Calculate payload attributes for slot `N` from the pinned/proposer head state.
4. Send the production FCU.
5. Propagate the production FCU execution payload context into the preparation future.

This guarantees randao, withdrawals, parent, and payload ID are all derived from the same production head.

## Payload ID Path

For `getPayloadId(parent, N)`:

1. Require a matching `BlockProductionPreparation` for parent and slot `N`.
2. Wait on its stable `executionPayloadContext`.
3. Fail clearly if there is no matching preparation, the preparation has expired, or the EL does not return a payload context.
4. Do not fallback to a fresh FCU recalculation.

This turns missing production preparation into an invariant violation instead of hidden recovery logic.

## Payload Attribute State Selection

`ProposersDataManager.calculatePayloadBuildingAttributes` must retrieve state from the head in the `ForkChoiceUpdateData` being prepared.

`getStateInEpoch` remains for proposer-connectivity checks. A separate helper should be used for payload-attribute state retrieval so ordinary and production FCUs both derive attributes from their own head.

## Testing

Add or update unit tests for:

- `getPayloadId` waits for pending production payload attributes and resolves through the preparation future.
- `getPayloadId` fails when no matching production preparation exists.
- Production preparation calculates payload attributes from the pinned/proposer head, not a later chain head.
- Non-production FCU updates are skipped when late-block reorg override is active.
- Payload attribute calculation retrieves state from the FCU head.
- Attestations due clears expired production preparation and prepares `slot + 1`.

Run:

```bash
./gradlew ethereum:statetransition:spotlessJavaCheck
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ProposersDataManagerTest
```
