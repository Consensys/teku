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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

class FastConfirmationTrackerTest {

  private final ReadOnlyStore store = mock(ReadOnlyStore.class);
  private final Checkpoint finalizedCheckpoint =
      new Checkpoint(UInt64.valueOf(12), Bytes32.random());

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
    final FastConfirmationTracker tracker = FastConfirmationTracker.create(Optional.empty());

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

    final SafeFuture<Void> result =
        tracker.onSlotHeadUpdated(
            UInt64.valueOf(13), Bytes32.random(), finalizedCheckpoint, false, false);

    assertThatSafeFuture(result).isCompleted();
    assertThat(asyncRunner.countDelayedActions()).isZero();
  }

  @Test
  void shouldScheduleUpdateOnAsyncRunnerWhenEnabledAndInitialized() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker =
        FastConfirmationTracker.create(Optional.of(asyncRunner));
    tracker.initialize(store);
    final Bytes32 headRoot = Bytes32.random();
    final Checkpoint greatestUnrealizedCheckpoint =
        new Checkpoint(UInt64.valueOf(13), Bytes32.random());

    final SafeFuture<Void> result =
        tracker.onSlotHeadUpdated(
            UInt64.valueOf(13), headRoot, greatestUnrealizedCheckpoint, false, true);

    assertThat(result).isNotDone();
    assertThat(asyncRunner.countDelayedActions()).isOne();

    asyncRunner.executeQueuedActions();

    assertThatSafeFuture(result).isCompleted();
    final FastConfirmationStore fastConfirmationStore =
        tracker.getFastConfirmationStore().orElseThrow();
    assertThat(fastConfirmationStore.previousEpochGreatestUnrealizedCheckpoint())
        .isEqualTo(greatestUnrealizedCheckpoint);
    assertThat(fastConfirmationStore.previousSlotHead()).isEqualTo(finalizedCheckpoint.getRoot());
    assertThat(fastConfirmationStore.currentSlotHead()).isEqualTo(headRoot);
  }
}
