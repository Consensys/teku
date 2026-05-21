# Minimal Fork Choice Pinned Block Production Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fallback payload-ID recalculation with a small pinned block-production record that is created before block production and consumed by `getPayloadId`.

**Architecture:** `ForkChoiceNotifierImpl` keeps ordinary FCU state in `forkChoiceUpdateData` and block-production FCU state in a separate `PinnedBlockProductionPreparation`. Production FCU data is parent/slot scoped, owns the stable payload-context future, and is cleared only by head advancement or attestation-due abandonment. `ProposersDataManager` calculates payload attributes from the head in the FCU being prepared.

**Tech Stack:** Java 25, Teku `SafeFuture`, JUnit 5, AssertJ, Mockito, Gradle.

---

## File Map

- Modify `ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java`
  - Rename `proposingSlot` parameters to `requestedBlockProductionSlot`.
  - Replace `lastProposingSlot` with `PinnedBlockProductionPreparation`.
  - Add late-block reorg override suppression.
  - Remove fallback recalculation from `internalGetPayloadId`.
  - Add pinned production FCU preparation and cleanup helpers.

- Modify `ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceUpdateData.java`
  - Add `withFreshForkChoiceState` for production FCU snapshots that must not inherit ordinary payload attributes.

- Modify `ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ProposersDataManager.java`
  - Keep `getStateInEpoch` for proposer connectivity.
  - Add a payload-attribute state helper that retrieves state from the FCU head.

- Modify `services/beaconchain/src/main/java/tech/pegasys/teku/services/beaconchain/BeaconChainController.java`
  - Pass `beaconConfig.eth2NetworkConfig().isForkChoiceLateBlockReorgEnabled()` into `ForkChoiceNotifierImpl`.

- Modify `ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java`
  - Add pinned-record tests.
  - Update payload-ID tests to use requested block production.
  - Remove tests that validate fallback recalculation.
  - Add late-block reorg suppression tests.

- Modify `ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ProposersDataManagerTest.java`
  - Add test that payload-attribute state is retrieved from the FCU head.

---

### Task 1: Wire Late-Block Reorg Flag Into The Notifier

**Files:**
- Modify: `ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java`
- Modify: `services/beaconchain/src/main/java/tech/pegasys/teku/services/beaconchain/BeaconChainController.java`
- Modify: `ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java`

- [ ] **Step 1: Write the failing test for override suppression**

Add this test to `ForkChoiceNotifierTest` near the existing `onForkChoiceUpdated_*` tests:

```java
@Test
void onForkChoiceUpdated_shouldSkipOrdinaryForkChoiceUpdateWhenLateBlockReorgOverrideIsActive() {
  final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
  final RecentChainData recentChainDataWithOverride = mock(RecentChainData.class);

  notifier =
      new ForkChoiceNotifierImpl(
          forkChoiceStateProvider,
          eventThread,
          timeProvider,
          spec,
          executionLayerChannel,
          recentChainDataWithOverride,
          proposersDataManager,
          true);
  notifier.onSyncingStatusChanged(true);
  notifier.subscribeToForkChoiceUpdatedResult(
      notification -> forkChoiceUpdatedResultNotification = notification);

  when(recentChainDataWithOverride.shouldOverrideForkChoiceUpdate(
          forkChoiceState.headBlock().blockRoot(), forkChoiceState.headBlockSlot()))
      .thenReturn(true);

  notifyForkChoiceUpdated(
      forkChoiceState, Optional.empty(), notification -> assertThat(notification).isNull());

  verify(recentChainDataWithOverride)
      .shouldOverrideForkChoiceUpdate(
          forkChoiceState.headBlock().blockRoot(), forkChoiceState.headBlockSlot());
  verifyNoInteractions(executionLayerChannel);
}
```

Add a companion test showing the flag gates the override check:

```java
@Test
void onForkChoiceUpdated_shouldNotCheckLateBlockReorgOverrideWhenDisabled() {
  final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
  final RecentChainData recentChainDataWithOverride = mock(RecentChainData.class);

  notifier =
      new ForkChoiceNotifierImpl(
          forkChoiceStateProvider,
          eventThread,
          timeProvider,
          spec,
          executionLayerChannel,
          recentChainDataWithOverride,
          proposersDataManager,
          false);
  notifier.onSyncingStatusChanged(true);
  notifier.subscribeToForkChoiceUpdatedResult(
      notification -> forkChoiceUpdatedResultNotification = notification);

  notifyForkChoiceUpdated(forkChoiceState);

  verify(recentChainDataWithOverride, never()).shouldOverrideForkChoiceUpdate(any(), any());
  verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.empty());
}
```

- [ ] **Step 2: Run the targeted failing tests**

Run:

```bash
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.onForkChoiceUpdated_shouldSkipOrdinaryForkChoiceUpdateWhenLateBlockReorgOverrideIsActive --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.onForkChoiceUpdated_shouldNotCheckLateBlockReorgOverrideWhenDisabled
```

Expected: compile failure because the constructor does not accept the boolean flag.

- [ ] **Step 3: Add constructor flag and suppression helper**

In `ForkChoiceNotifierImpl`, add the field:

```java
private final boolean forkChoiceLateBlockReorgEnabled;
```

Update the constructor signature and body:

```java
public ForkChoiceNotifierImpl(
    final ForkChoiceStateProvider forkChoiceStateProvider,
    final EventThread eventThread,
    final TimeProvider timeProvider,
    final Spec spec,
    final ExecutionLayerChannel executionLayerChannel,
    final RecentChainData recentChainData,
    final ProposersDataManager proposersDataManager,
    final boolean forkChoiceLateBlockReorgEnabled) {
  this.forkChoiceStateProvider = forkChoiceStateProvider;
  this.eventThread = eventThread;
  this.spec = spec;
  this.executionLayerChannel = executionLayerChannel;
  this.recentChainData = recentChainData;
  this.proposersDataManager = proposersDataManager;
  this.timeProvider = timeProvider;
  this.forkChoiceLateBlockReorgEnabled = forkChoiceLateBlockReorgEnabled;
}
```

Add this helper:

```java
private boolean shouldSkipForkChoiceUpdateDueToLateBlockReorg(
    final ForkChoiceState forkChoiceState) {
  return forkChoiceLateBlockReorgEnabled
      && recentChainData.shouldOverrideForkChoiceUpdate(
          forkChoiceState.headBlock().blockRoot(), forkChoiceState.headBlockSlot());
}
```

In `internalForkChoiceUpdated`, before replacing ordinary `forkChoiceUpdateData` for the empty requested block production path, add:

```java
if (requestedBlockProductionSlot.isEmpty()
    && shouldSkipForkChoiceUpdateDueToLateBlockReorg(forkChoiceState)) {
  LOG.debug(
      "internalForkChoiceUpdated skipped ordinary FCU because late block reorg override is active for head {} at slot {}",
      forkChoiceState.headBlock(),
      forkChoiceState.headBlockSlot());
  return;
}
```

- [ ] **Step 4: Update constructor call sites**

In `BeaconChainController`, change the notifier construction to:

```java
forkChoiceNotifier =
    new ForkChoiceNotifierImpl(
        forkChoiceStateProvider,
        eventThread,
        timeProvider,
        spec,
        executionLayer,
        recentChainData,
        proposersDataManager,
        beaconConfig.eth2NetworkConfig().isForkChoiceLateBlockReorgEnabled());
```

In `ForkChoiceNotifierTest#setUp`, pass `false`:

```java
notifier =
    new ForkChoiceNotifierImpl(
        forkChoiceStateProvider,
        eventThread,
        timeProvider,
        spec,
        executionLayerChannel,
        recentChainData,
        proposersDataManager,
        false);
```

In `ForkChoiceNotifierTest#reInitializePreMerge`, pass `false` in the same way.

In the existing tests that manually rebuild the notifier, pass `false` unless the test is explicitly enabling the override behavior.

- [ ] **Step 5: Run the tests**

Run:

```bash
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.onForkChoiceUpdated_shouldSkipOrdinaryForkChoiceUpdateWhenLateBlockReorgOverrideIsActive --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.onForkChoiceUpdated_shouldNotCheckLateBlockReorgOverrideWhenDisabled
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java services/beaconchain/src/main/java/tech/pegasys/teku/services/beaconchain/BeaconChainController.java ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java
git commit -m "Restore late block reorg FCU suppression"
```

---

### Task 2: Add Pinned Block Production Tests

**Files:**
- Modify: `ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java`

- [ ] **Step 1: Rename test helper parameters**

Rename helper parameters from `proposingSlot` to `requestedBlockProductionSlot` so tests use the same language as production code:

```java
private void notifyForkChoiceUpdatedVerifyNoNotification(
    final ForkChoiceState forkChoiceState,
    final Optional<UInt64> requestedBlockProductionSlot) {
  notifyForkChoiceUpdated(
      forkChoiceState,
      requestedBlockProductionSlot,
      notification -> assertThat(notification).isNull());
}

private void notifyForkChoiceUpdated(
    final ForkChoiceState forkChoiceState,
    final Optional<UInt64> requestedBlockProductionSlot) {
  notifyForkChoiceUpdated(
      forkChoiceState,
      requestedBlockProductionSlot,
      notification -> assertThat(notification).isNotNull());
}

private void notifyForkChoiceUpdated(
    final ForkChoiceState forkChoiceState,
    final Optional<UInt64> requestedBlockProductionSlot,
    final Consumer<ForkChoiceUpdatedResultNotification> requirements) {
  forkChoiceUpdatedResultNotification = null;
  notifier.onForkChoiceUpdated(forkChoiceState, requestedBlockProductionSlot);
  requirements.accept(forkChoiceUpdatedResultNotification);
}
```

- [ ] **Step 2: Add test for pending production attributes**

Add this test near the `getPayloadId_*` tests:

```java
@Test
@SuppressWarnings("unchecked")
void getPayloadId_shouldWaitForPinnedBlockProductionPayloadAttributes() {
  final Bytes8 payloadId = dataStructureUtil.randomBytes8();
  final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
  final BeaconState headState = getHeadState();
  final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
  final UInt64 blockSlot = headState.getSlot().plus(1);
  final PayloadBuildingAttributes payloadBuildingAttributes =
      withProposerForSlot(forkChoiceState, headState, blockSlot);

  final AtomicReference<SafeFuture<Optional<PayloadBuildingAttributes>>> actualAttributesFuture =
      new AtomicReference<>();
  final SafeFuture<Optional<PayloadBuildingAttributes>> deferredAttributesFuture =
      new SafeFuture<>();
  doAnswer(
          invocation -> {
            actualAttributesFuture.set(
                (SafeFuture<Optional<PayloadBuildingAttributes>>) invocation.callRealMethod());
            return deferredAttributesFuture;
          })
      .when(proposersDataManager)
      .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

  final SafeFuture<ForkChoiceUpdatedResult> responseFuture = new SafeFuture<>();
  when(executionLayerChannel.engineForkChoiceUpdated(
          forkChoiceState, Optional.of(payloadBuildingAttributes)))
      .thenReturn(responseFuture);

  notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

  final SafeFuture<Optional<ExecutionPayloadContext>> payloadContextFuture =
      notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot);
  assertThatSafeFuture(payloadContextFuture).isNotCompleted();

  actualAttributesFuture.get().propagateTo(deferredAttributesFuture);
  assertThatSafeFuture(payloadContextFuture).isNotCompleted();

  final ExecutionPayloadContext executionPayloadContext =
      new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes);
  responseFuture.complete(
      createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));

  assertThatSafeFuture(payloadContextFuture)
      .isCompletedWithOptionalContaining(executionPayloadContext);
}
```

- [ ] **Step 3: Add test that `getPayloadId` does not clear the pinned record**

Add:

```java
@Test
void getPayloadId_shouldKeepPinnedBlockProductionForSameSlotRetry() {
  final Bytes8 payloadId = dataStructureUtil.randomBytes8();
  final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
  final BeaconState headState = getHeadState();
  final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
  final UInt64 blockSlot = headState.getSlot().plus(1);
  final PayloadBuildingAttributes payloadBuildingAttributes =
      withProposerForSlot(forkChoiceState, headState, blockSlot);

  when(executionLayerChannel.engineForkChoiceUpdated(
          forkChoiceState, Optional.of(payloadBuildingAttributes)))
      .thenReturn(
          SafeFuture.completedFuture(
              createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId))));

  notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

  final ExecutionPayloadContext executionPayloadContext =
      new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes);
  assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
      .isCompletedWithOptionalContaining(executionPayloadContext);
  assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
      .isCompletedWithOptionalContaining(executionPayloadContext);
}
```

- [ ] **Step 4: Add tests for cleanup signals**

Add:

```java
@Test
void getPayloadId_shouldFailAfterHeadAdvancementClearsPinnedBlockProduction() {
  final Bytes8 payloadId = dataStructureUtil.randomBytes8();
  final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
  final BeaconState headState = getHeadState();
  final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
  final UInt64 blockSlot = headState.getSlot().plus(1);
  final PayloadBuildingAttributes payloadBuildingAttributes =
      withProposerForSlot(forkChoiceState, headState, blockSlot);

  when(executionLayerChannel.engineForkChoiceUpdated(
          forkChoiceState, Optional.of(payloadBuildingAttributes)))
      .thenReturn(
          SafeFuture.completedFuture(
              createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId))));

  notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
  storageSystem.chainUpdater().updateBestBlock(storageSystem.chainUpdater().advanceChain());
  notifyForkChoiceUpdated(getCurrentForkChoiceState());

  assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
      .isCompletedExceptionally();
}

@Test
void getPayloadId_shouldFailAfterAttestationsDueClearsPinnedBlockProduction() {
  final Bytes8 payloadId = dataStructureUtil.randomBytes8();
  final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
  final BeaconState headState = getHeadState();
  final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
  final UInt64 blockSlot = headState.getSlot().plus(1);
  final PayloadBuildingAttributes payloadBuildingAttributes =
      withProposerForSlot(forkChoiceState, headState, blockSlot);

  when(executionLayerChannel.engineForkChoiceUpdated(
          forkChoiceState, Optional.of(payloadBuildingAttributes)))
      .thenReturn(
          SafeFuture.completedFuture(
              createForkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId))));

  notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
  notifier.onAttestationsDue(blockSlot);

  assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
      .isCompletedExceptionally();
}
```

- [ ] **Step 5: Add test for stale pinned async attributes after abandonment**

Add:

```java
@Test
@SuppressWarnings("unchecked")
void onForkChoiceUpdated_shouldIgnoreStalePinnedBlockProductionPayloadAttributesAfterAbandonment() {
  final ForkChoiceState forkChoiceState = getCurrentForkChoiceState();
  final BeaconState headState = getHeadState();
  final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
  final UInt64 blockSlot = headState.getSlot().plus(1);
  withProposerForSlot(forkChoiceState, headState, blockSlot);

  final AtomicReference<SafeFuture<Optional<PayloadBuildingAttributes>>> actualAttributesFuture =
      new AtomicReference<>();
  final SafeFuture<Optional<PayloadBuildingAttributes>> deferredAttributesFuture =
      new SafeFuture<>();
  doAnswer(
          invocation -> {
            actualAttributesFuture.set(
                (SafeFuture<Optional<PayloadBuildingAttributes>>) invocation.callRealMethod());
            return deferredAttributesFuture;
          })
      .when(proposersDataManager)
      .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

  notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
  notifier.onAttestationsDue(blockSlot);

  actualAttributesFuture.get().propagateTo(deferredAttributesFuture);

  verifyNoInteractions(executionLayerChannel);
  assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
      .isCompletedExceptionally();
}
```

- [ ] **Step 6: Run failing tests**

Run:

```bash
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.getPayloadId_shouldWaitForPinnedBlockProductionPayloadAttributes --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.getPayloadId_shouldKeepPinnedBlockProductionForSameSlotRetry --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.getPayloadId_shouldFailAfterHeadAdvancementClearsPinnedBlockProduction --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.getPayloadId_shouldFailAfterAttestationsDueClearsPinnedBlockProduction --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.onForkChoiceUpdated_shouldIgnoreStalePinnedBlockProductionPayloadAttributesAfterAbandonment
```

Expected: at least one test fails because pinned block-production state does not exist yet.

- [ ] **Step 7: Commit tests**

```bash
git add ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java
git commit -m "Add pinned block production notifier tests"
```

---

### Task 3: Implement `PinnedBlockProductionPreparation`

**Files:**
- Modify: `ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java`
- Modify: `ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceUpdateData.java`

- [ ] **Step 1: Add the pinned record and field**

Replace the `lastProposingSlot` field and comment with:

```java
// Stable block-production state between requested block production and getPayloadId.
// Ordinary FCUs may continue to advance separately, but getPayloadId must consume this pinned
// record for its parent and slot.
private Optional<PinnedBlockProductionPreparation> pinnedBlockProductionPreparation =
    Optional.empty();
```

Add this private record inside `ForkChoiceNotifierImpl`:

```java
private record PinnedBlockProductionPreparation(
    UInt64 slot,
    ForkChoiceNode parentBeaconBlock,
    ForkChoiceUpdateData forkChoiceUpdateData,
    SafeFuture<Optional<ExecutionPayloadContext>> executionPayloadContext) {

  boolean matches(final ForkChoiceNode parentBeaconBlock, final UInt64 slot) {
    return this.slot.equals(slot) && this.parentBeaconBlock.equals(parentBeaconBlock);
  }

  boolean isExpiredBySlot(final UInt64 slot) {
    return this.slot.isLessThanOrEqualTo(slot);
  }

  boolean isClearedByHead(final ForkChoiceState forkChoiceState) {
    return forkChoiceState.headBlockSlot().isGreaterThanOrEqualTo(slot);
  }

  PinnedBlockProductionPreparation withForkChoiceUpdateData(
      final ForkChoiceUpdateData forkChoiceUpdateData) {
    return new PinnedBlockProductionPreparation(
        slot, parentBeaconBlock, forkChoiceUpdateData, executionPayloadContext);
  }
}
```

- [ ] **Step 2: Add fresh FCU snapshot method**

In `ForkChoiceUpdateData`, add this method after `withForkChoiceState`:

```java
public ForkChoiceUpdateData withFreshForkChoiceState(final ForkChoiceState forkChoiceState) {
  return new ForkChoiceUpdateData(forkChoiceState, Optional.empty(), terminalBlockHash);
}
```

Production uses this even when the head is unchanged. That prevents a pinned block-production FCU from inheriting ordinary payload attributes for another slot.

- [ ] **Step 3: Rename public notifier parameter**

Update the `onForkChoiceUpdated` signature implementation:

```java
@Override
public void onForkChoiceUpdated(
    final ForkChoiceState forkChoiceState, final Optional<UInt64> requestedBlockProductionSlot) {
  eventThread.execute(
      () -> internalForkChoiceUpdated(forkChoiceState, requestedBlockProductionSlot));
}
```

Update the private method signature:

```java
private void internalForkChoiceUpdated(
    final ForkChoiceState forkChoiceState, final Optional<UInt64> requestedBlockProductionSlot) {
```

- [ ] **Step 4: Add cleanup helpers**

Add:

```java
private void clearPinnedBlockProductionIfHeadAdvanced(final ForkChoiceState forkChoiceState) {
  pinnedBlockProductionPreparation
      .filter(preparation -> preparation.isClearedByHead(forkChoiceState))
      .ifPresent(
          preparation -> {
            LOG.debug(
                "Clearing pinned block production for slot {} because head advanced to slot {}",
                preparation.slot(),
                forkChoiceState.headBlockSlot());
            pinnedBlockProductionPreparation = Optional.empty();
          });
}

private void clearPinnedBlockProductionIfExpired(final UInt64 slot) {
  pinnedBlockProductionPreparation
      .filter(preparation -> preparation.isExpiredBySlot(slot))
      .ifPresent(
          preparation -> {
            LOG.debug(
                "Clearing pinned block production for slot {} because attestations are due for slot {}",
                preparation.slot(),
                slot);
            pinnedBlockProductionPreparation = Optional.empty();
          });
}
```

In `prepareNextSlotProposal`, call expiry cleanup before preparing `slot + 1`:

```java
private void prepareNextSlotProposal(final UInt64 slot) {
  clearPinnedBlockProductionIfExpired(slot);
  updatePayloadAttributes(slot.plus(1));
}
```

- [ ] **Step 5: Add block-production fork choice pinning helper**

Add:

```java
private void pinForkChoiceStateForBlockProduction(
    final ForkChoiceState forkChoiceState, final UInt64 requestedBlockProductionSlot) {
  final ForkChoiceUpdateData productionForkChoiceUpdateData =
      forkChoiceUpdateData.withFreshForkChoiceState(forkChoiceState);
  final PinnedBlockProductionPreparation preparation =
      new PinnedBlockProductionPreparation(
          requestedBlockProductionSlot,
          forkChoiceState.headBlock(),
          productionForkChoiceUpdateData,
          new SafeFuture<>());
  pinnedBlockProductionPreparation = Optional.of(preparation);

  LOG.debug(
      "Pinned block production for slot {} with forkChoiceUpdateData {}",
      requestedBlockProductionSlot,
      productionForkChoiceUpdateData);

  updatePayloadAttributesForPinnedBlockProduction(preparation);
}
```

Add:

```java
private void updatePayloadAttributesForPinnedBlockProduction(
    final PinnedBlockProductionPreparation preparation) {
  final ForkChoiceUpdateData localForkChoiceUpdateData = preparation.forkChoiceUpdateData();
  localForkChoiceUpdateData
      .withPayloadBuildingAttributesAsync(
          () ->
              proposersDataManager.calculatePayloadBuildingAttributes(
                  preparation.slot(), inSync, localForkChoiceUpdateData, true),
          eventThread)
      .thenAccept(
          maybeForkChoiceUpdateData -> {
            if (maybeForkChoiceUpdateData.isEmpty()) {
              if (isCurrentPinnedBlockProduction(preparation)) {
                preparation.executionPayloadContext().complete(Optional.empty());
              }
              return;
            }
            if (!isCurrentPinnedBlockProduction(preparation)) {
              LOG.debug(
                  "Ignoring stale pinned block production payload attributes for slot {}",
                  preparation.slot());
              return;
            }

            final ForkChoiceUpdateData updatedForkChoiceUpdateData =
                maybeForkChoiceUpdateData.get();
            final PinnedBlockProductionPreparation updatedPreparation =
                preparation.withForkChoiceUpdateData(updatedForkChoiceUpdateData);
            pinnedBlockProductionPreparation = Optional.of(updatedPreparation);
            updatedForkChoiceUpdateData
                .getExecutionPayloadContext()
                .propagateTo(updatedPreparation.executionPayloadContext());
            sendForkChoiceUpdated(updatedForkChoiceUpdateData);
          })
      .finish(
          error -> {
            if (isCurrentPinnedBlockProduction(preparation)) {
              preparation.executionPayloadContext().completeExceptionally(error);
            }
            LOG.error(
                "Failed to calculate pinned block production payload attributes for slot {}",
                preparation.slot(),
                error);
          });
}
```

Add:

```java
@SuppressWarnings("ReferenceComparison")
private boolean isCurrentPinnedBlockProduction(
    final PinnedBlockProductionPreparation preparation) {
  return pinnedBlockProductionPreparation
      .map(currentPreparation -> currentPreparation == preparation)
      .orElse(false);
}
```

- [ ] **Step 6: Send FCU snapshots explicitly**

Replace the body of `sendForkChoiceUpdated()` with a delegating call:

```java
private void sendForkChoiceUpdated() {
  sendForkChoiceUpdated(forkChoiceUpdateData);
}
```

Add overload:

```java
private void sendForkChoiceUpdated(final ForkChoiceUpdateData forkChoiceUpdateData) {
  forkChoiceUpdateData
      .send(executionLayerChannel, timeProvider.getTimeInMillis())
      .ifPresent(
          forkChoiceUpdatedResultFuture ->
              forkChoiceUpdatedSubscribers.deliver(
                  ForkChoiceUpdatedResultSubscriber::onForkChoiceUpdatedResult,
                  new ForkChoiceUpdatedResultNotification(
                      forkChoiceUpdateData.getForkChoiceState(),
                      forkChoiceUpdateData.getPayloadBuildingAttributes(),
                      forkChoiceUpdateData.hasTerminalBlockHash(),
                      forkChoiceUpdatedResultFuture)));
}
```

- [ ] **Step 7: Route production path through the pinned record**

In `internalForkChoiceUpdated`, replace the `if (proposingSlot.isPresent())` branch with:

```java
clearPinnedBlockProductionIfHeadAdvanced(forkChoiceState);

if (requestedBlockProductionSlot.isPresent()) {
  pinForkChoiceStateForBlockProduction(forkChoiceState, requestedBlockProductionSlot.orElseThrow());
  return;
}
```

Remove branches that compare `lastProposingSlot`.

- [ ] **Step 8: Run targeted tests**

Run:

```bash
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.getPayloadId_shouldWaitForPinnedBlockProductionPayloadAttributes --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.getPayloadId_shouldKeepPinnedBlockProductionForSameSlotRetry --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.getPayloadId_shouldFailAfterHeadAdvancementClearsPinnedBlockProduction --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest.getPayloadId_shouldFailAfterAttestationsDueClearsPinnedBlockProduction
```

Expected: tests still fail until `getPayloadId` uses the pinned record in Task 4.

- [ ] **Step 9: Commit**

```bash
git add ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceUpdateData.java
git commit -m "Add pinned block production preparation"
```

---

### Task 4: Remove `internalGetPayloadId` Fallback

**Files:**
- Modify: `ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java`
- Modify: `ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java`

- [ ] **Step 1: Replace payload ID lookup with pinned record lookup**

Replace `internalGetPayloadId` with:

```java
private SafeFuture<Optional<ExecutionPayloadContext>> internalGetPayloadId(
    final ForkChoiceNode parentBeaconBlock, final UInt64 blockSlot) {
  eventThread.checkOnEventThread();

  LOG.debug(
      "internalGetPayloadId parentBeaconBlock {} blockSlot {}", parentBeaconBlock, blockSlot);

  final Bytes32 parentExecutionHash =
      recentChainData
          .getExecutionBlockHashForBlock(parentBeaconBlock)
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Failed to retrieve execution payload hash from beacon block root"));

  if (parentExecutionHash.isZero() && !forkChoiceUpdateData.hasTerminalBlockHash()) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  final PinnedBlockProductionPreparation preparation =
      pinnedBlockProductionPreparation
          .filter(candidate -> candidate.matches(parentBeaconBlock, blockSlot))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "No pinned block production preparation for parent "
                          + parentBeaconBlock
                          + " at slot "
                          + blockSlot));

  return preparation
      .executionPayloadContext()
      .thenApply(
          maybeExecutionPayloadContext -> {
            if (maybeExecutionPayloadContext.isEmpty()) {
              throw new IllegalStateException("Unable to obtain an executionPayloadContext");
            }

            final ExecutionPayloadContext executionPayloadContext =
                maybeExecutionPayloadContext.get();
            final UInt64 timestamp =
                spec.computeTimeAtSlot(blockSlot, recentChainData.getGenesisTime());
            checkState(
                executionPayloadContext.getForkChoiceState().headBlock().equals(parentBeaconBlock),
                "Pinned block production head %s does not match requested block production parent %s",
                executionPayloadContext.getForkChoiceState().headBlock(),
                parentBeaconBlock);
            checkState(
                executionPayloadContext.getParentHash().equals(parentExecutionHash),
                "Pinned block production parent execution hash %s does not match requested parent execution hash %s",
                executionPayloadContext.getParentHash(),
                parentExecutionHash);
            checkState(
                executionPayloadContext.getPayloadBuildingAttributes().timestamp().equals(timestamp),
                "Pinned block production timestamp %s does not match requested timestamp %s",
                executionPayloadContext.getPayloadBuildingAttributes().timestamp(),
                timestamp);
            return maybeExecutionPayloadContext;
          });
}
```

Remove `validateForkChoiceHeadMatchesParent`.

- [ ] **Step 2: Update payload ID tests to request block production first**

In these tests, replace `notifyForkChoiceUpdated(forkChoiceState);` with `notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));`:

```java
getPayloadId_shouldReturnLatestPayloadId
getPayloadId_shouldReturnLatestPayloadIdWithValidatorRegistration
getPayloadId_shouldReturnExceptionallyLatestPayloadIdOnWrongRoot
```

- [ ] **Step 3: Remove fallback-specific tests and helper**

Delete these tests because the fallback they verify is intentionally removed:

```java
getPayloadId_shouldObtainAPayloadIdWhenProposingTheMergeBlock
getPayloadId_shouldObtainAPayloadIdOnPostMergeBlockNonFinalized
getPayloadId_shouldObtainAPayloadIdOnPostMergeBlockFinalized
getPayloadId_shouldObtainAPayloadIdOnPostMergeBlockFinalizedEvenIfProposerNotPrepared
getPayloadId_shouldFailIfDefaultFeeRecipientIsNotConfigured
```

Delete:

```java
private void validateGetPayloadIdOnTheFlyRetrieval(...)
```

- [ ] **Step 4: Keep first-FCU tests aligned with the new invariant**

Keep `getPayloadId_shouldReturnExceptionallyBeforeTheFirstForkChoiceState` and `getPayloadId_preMergeShouldReturnEmptyBeforeTheFirstForkChoiceState`.

The post-merge test should still assert exceptional completion when no pinned production record exists:

```java
assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
    .isCompletedExceptionally();
```

The pre-merge test should still assert empty optional:

```java
assertThatSafeFuture(notifier.getPayloadId(ForkChoiceNode.createBase(blockRoot), blockSlot))
    .isCompletedWithEmptyOptional();
```

- [ ] **Step 5: Run notifier tests**

Run:

```bash
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest
```

Expected: failures remain only around payload attribute state selection and stale ordinary tests that are updated in later tasks.

- [ ] **Step 6: Commit**

```bash
git add ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java
git commit -m "Use pinned block production for payload IDs"
```

---

### Task 5: Calculate Payload Attributes From The FCU Head

**Files:**
- Modify: `ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ProposersDataManager.java`
- Modify: `ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ProposersDataManagerTest.java`

- [ ] **Step 1: Write failing state-selection test**

Add imports to `ProposersDataManagerTest`:

```java
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
```

Add:

```java
@TestTemplate
void calculatePayloadBuildingAttributes_shouldRetrieveStateFromForkChoiceUpdateHead() {
  final UInt64 blockSlot = UInt64.valueOf(2);
  final Bytes32 forkChoiceHeadRoot = dataStructureUtil.randomBytes32();
  final ForkChoiceState forkChoiceState =
      new ForkChoiceState(
          ForkChoiceNode.createBase(forkChoiceHeadRoot),
          UInt64.ONE,
          UInt64.ONE,
          dataStructureUtil.randomBytes32(),
          dataStructureUtil.randomBytes32(),
          dataStructureUtil.randomBytes32(),
          false);
  final ForkChoiceUpdateData forkChoiceUpdateData =
      new ForkChoiceUpdateData(forkChoiceState, Optional.empty(), Optional.empty());

  manager.calculatePayloadBuildingAttributes(blockSlot, true, forkChoiceUpdateData, false).join();

  verify(recentChainData)
      .retrieveBlockState(new SlotAndBlockRoot(blockSlot, forkChoiceHeadRoot));
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ProposersDataManagerTest.calculatePayloadBuildingAttributes_shouldRetrieveStateFromForkChoiceUpdateHead
```

Expected: FAIL because current code uses `getStateInEpoch`.

- [ ] **Step 3: Add payload-attribute state helper**

In `ProposersDataManager`, replace:

```java
return getStateInEpoch(epoch)
    .thenApplyAsync(
        maybeState ->
            calculatePayloadBuildingAttributes(
                currentHeadBlock, blockSlot, epoch, maybeState, mandatory),
        eventThread);
```

with:

```java
return getStateForPayloadBuildingAttributes(blockSlot, forkChoiceState)
    .thenApplyAsync(
        maybeState ->
            calculatePayloadBuildingAttributes(
                currentHeadBlock, blockSlot, epoch, maybeState, mandatory),
        eventThread);
```

Add helper below `calculatePayloadBuildingAttributes`:

```java
private SafeFuture<Optional<BeaconState>> getStateForPayloadBuildingAttributes(
    final UInt64 blockSlot, final ForkChoiceState forkChoiceState) {
  final Bytes32 headRoot = forkChoiceState.headBlock().blockRoot();
  if (headRoot.isZero()) {
    return recentChainData
        .getBestBlockRoot()
        .map(bestBlockRoot -> recentChainData.retrieveBlockState(new SlotAndBlockRoot(blockSlot, bestBlockRoot)))
        .orElse(SafeFuture.completedFuture(Optional.empty()));
  }
  return recentChainData.retrieveBlockState(new SlotAndBlockRoot(blockSlot, headRoot));
}
```

Add import:

```java
import org.apache.tuweni.bytes.Bytes32;
```

- [ ] **Step 4: Run ProposersDataManager tests**

Run:

```bash
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ProposersDataManagerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ProposersDataManager.java ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ProposersDataManagerTest.java
git commit -m "Calculate payload attributes from FCU head"
```

---

### Task 6: Stabilize Ordinary FCU Tests Around The Pinned Record

**Files:**
- Modify: `ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java`
- Modify: `ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java`

- [ ] **Step 1: Update consecutive-production test expectation**

In `onForkChoiceUpdated_shouldSendNotificationWithStableSlot`, keep the first requested block production call:

```java
notifyForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
verify(executionLayerChannel)
    .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(0)));
```

After `notifier.onAttestationsDue(blockSlot);`, keep the expectation that slot `N + 1` attributes are sent:

```java
verify(executionLayerChannel)
    .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes.get(1)));
```

Keep the final requested block production call for `nextBlockSlot`, but treat it as reuse of the already-sent FCU inside the resend window:

```java
notifyForkChoiceUpdated(forkChoiceState, Optional.of(nextBlockSlot));
verifyNoMoreInteractions(executionLayerChannel);
```

This call should reuse the already-sent ordinary FCU only if the `ForkChoiceUpdateData` snapshot is identical and still inside the resend window.

- [ ] **Step 2: Ensure stale pinned async results complete predictably**

In `updatePayloadAttributesForPinnedBlockProduction`, when a stale pinned calculation returns, complete the stale record future with empty before returning:

```java
if (!isCurrentPinnedBlockProduction(preparation)) {
  preparation.executionPayloadContext().complete(Optional.empty());
  LOG.debug(
      "Ignoring stale pinned block production payload attributes for slot {}",
      preparation.slot());
  return;
}
```

- [ ] **Step 3: Run notifier tests**

Run:

```bash
./gradlew ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java
git commit -m "Stabilize pinned block production tests"
```

---

### Task 7: Polish Logs And Remove Stale Terminology

**Files:**
- Modify: `ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java`
- Modify: `ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java`

- [ ] **Step 1: Remove remaining old terminology**

Run:

```bash
rg -n "lastProposingSlot|proposingSlot|pinned for slot|Pin-hold|pin" ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java
```

Replace old names with:

```text
requestedBlockProductionSlot
pinned block production
PinnedBlockProductionPreparation
```

Keep the word `pinned` only when it refers to pinned block production.

- [ ] **Step 2: Ensure logs describe the active object**

Use logs shaped like:

```java
LOG.debug(
    "Pinned block production for slot {} with forkChoiceUpdateData {}",
    requestedBlockProductionSlot,
    productionForkChoiceUpdateData);
```

```java
LOG.debug(
    "Clearing pinned block production for slot {} because head advanced to slot {}",
    preparation.slot(),
    forkChoiceState.headBlockSlot());
```

```java
LOG.debug(
    "Clearing pinned block production for slot {} because attestations are due for slot {}",
    preparation.slot(),
    slot);
```

- [ ] **Step 3: Run formatting check**

Run:

```bash
./gradlew ethereum:statetransition:spotlessJavaCheck
```

Expected: PASS. If it fails, run:

```bash
./gradlew spotlessApply
```

Then rerun:

```bash
./gradlew ethereum:statetransition:spotlessJavaCheck
```

- [ ] **Step 4: Commit**

```bash
git add ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java
git commit -m "Clarify pinned block production terminology"
```

---

### Task 8: Final Verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew ethereum:statetransition:spotlessJavaCheck ethereum:statetransition:test --tests tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierTest --tests tech.pegasys.teku.statetransition.forkchoice.ProposersDataManagerTest
```

Expected: PASS.

- [ ] **Step 2: Run module tests**

Run:

```bash
./gradlew ethereum:statetransition:test
```

Expected: PASS.

- [ ] **Step 3: Compile beaconchain service**

Run:

```bash
./gradlew services:beaconchain:compileJava
```

Expected: PASS.

- [ ] **Step 4: Check diff hygiene**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` prints no output. `git status --short` only shows intentional modified files if the final verification commit has not been made.

- [ ] **Step 5: Commit final verification notes if files changed**

If Task 8 required formatting or test-only adjustments, commit them:

```bash
git add ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierImpl.java ethereum/statetransition/src/main/java/tech/pegasys/teku/statetransition/forkchoice/ProposersDataManager.java ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ForkChoiceNotifierTest.java ethereum/statetransition/src/test/java/tech/pegasys/teku/statetransition/forkchoice/ProposersDataManagerTest.java services/beaconchain/src/main/java/tech/pegasys/teku/services/beaconchain/BeaconChainController.java
git commit -m "Verify pinned block production notifier changes"
```
