/*
 * Copyright Consensys Software Inc., 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

class FastConfirmationTrackerTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final ReadOnlyStore store = mock(ReadOnlyStore.class);
  private final Checkpoint finalizedCheckpoint =
      new Checkpoint(UInt64.valueOf(12), Bytes32.random());

  @BeforeEach
  void setUp() {
    // No source states available: get_latest_confirmed is skipped and the confirmed root is left
    // unchanged, so these tests exercise only update_fast_confirmation_variables and the guard.
    when(store.retrieveCheckpointState(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(store.retrieveBlockState(any(Bytes32.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  void shouldNotInitializeStoreWhenDisabled() {
    final FastConfirmationTracker tracker = FastConfirmationTracker.NOOP;

    tracker.initialize(store);

    assertThat(tracker.isEnabled()).isFalse();
    assertThat(tracker.getFastConfirmationStore()).isEmpty();
  }

  @Test
  void shouldInitializeStoreFromFinalizedCheckpointWhenEnabled() {
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker = FastConfirmationTracker.create(spec, Optional.empty());

    tracker.initialize(store);

    final FastConfirmationStore fastConfirmationStore =
        tracker.getFastConfirmationStore().orElseThrow();
    assertThat(tracker.isEnabled()).isTrue();
    assertThat(fastConfirmationStore.store()).isSameAs(store);
    assertThat(fastConfirmationStore.confirmedRoot()).isEqualTo(finalizedCheckpoint.getRoot());
    assertThat(fastConfirmationStore.previousEpochObservedJustifiedCheckpoint())
        .isEqualTo(finalizedCheckpoint);
    assertThat(fastConfirmationStore.currentEpochObservedJustifiedCheckpoint())
        .isEqualTo(finalizedCheckpoint);
    assertThat(fastConfirmationStore.previousEpochGreatestUnrealizedCheckpoint())
        .isEqualTo(finalizedCheckpoint);
    assertThat(fastConfirmationStore.previousSlotHead()).isEqualTo(finalizedCheckpoint.getRoot());
    assertThat(fastConfirmationStore.currentSlotHead()).isEqualTo(finalizedCheckpoint.getRoot());
  }

  @Test
  void shouldNotScheduleUpdateWhenDisabled() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    final FastConfirmationTracker tracker = FastConfirmationTracker.NOOP;

    final SafeFuture<Void> result = tracker.onSlot(UInt64.valueOf(13), Bytes32.random());

    assertThatSafeFuture(result).isCompleted();
    assertThat(asyncRunner.countDelayedActions()).isZero();
  }

  @Test
  void shouldScheduleUpdateOnAsyncRunnerWhenEnabledAndInitialized() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(spec, Optional.of(asyncRunner));
    tracker.initialize(store);
    final Bytes32 headRoot = Bytes32.random();

    // Slot 13 is a mid-epoch slot (minimal SLOTS_PER_EPOCH == 8), so only the slot heads rotate.
    final SafeFuture<Void> result = tracker.onSlot(UInt64.valueOf(13), headRoot);

    assertThat(result).isNotDone();
    assertThat(asyncRunner.countDelayedActions()).isOne();

    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompleted();
    final FastConfirmationStore fastConfirmationStore =
        tracker.getFastConfirmationStore().orElseThrow();
    assertThat(fastConfirmationStore.previousSlotHead()).isEqualTo(finalizedCheckpoint.getRoot());
    assertThat(fastConfirmationStore.currentSlotHead()).isEqualTo(headRoot);
  }

  @Test
  void shouldSkipStaleOrDuplicateSlotUpdates() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(spec, Optional.of(asyncRunner));
    tracker.initialize(store);

    final Bytes32 headA = Bytes32.random();
    final Bytes32 headB = Bytes32.random();
    final Bytes32 headC = Bytes32.random();

    // Slot 13: rotates current -> previous, sets current = headA
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), headA);
    assertThat(currentSlotHead(tracker)).isEqualTo(headA);
    assertThat(previousSlotHead(tracker)).isEqualTo(finalizedCheckpoint.getRoot());

    // Slot 14: rotates again, previous = headA, current = headB
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(14), headB);
    assertThat(currentSlotHead(tracker)).isEqualTo(headB);
    assertThat(previousSlotHead(tracker)).isEqualTo(headA);

    // Duplicate slot 14 must be ignored (no second rotation)
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(14), headC);
    assertThat(currentSlotHead(tracker)).isEqualTo(headB);
    assertThat(previousSlotHead(tracker)).isEqualTo(headA);

    // Older slot 13 must be ignored as well
    applyUpdate(tracker, asyncRunner, UInt64.valueOf(13), headC);
    assertThat(currentSlotHead(tracker)).isEqualTo(headB);
    assertThat(previousSlotHead(tracker)).isEqualTo(headA);
  }

  private void applyUpdate(
      final FastConfirmationTracker tracker,
      final StubAsyncRunner asyncRunner,
      final UInt64 slot,
      final Bytes32 headRoot) {
    final SafeFuture<Void> result = tracker.onSlot(slot, headRoot);
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompleted();
  }

  private Bytes32 currentSlotHead(final FastConfirmationTracker tracker) {
    return tracker.getFastConfirmationStore().orElseThrow().currentSlotHead();
  }

  private Bytes32 previousSlotHead(final FastConfirmationTracker tracker) {
    return tracker.getFastConfirmationStore().orElseThrow().previousSlotHead();
  }
}
