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

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
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
    final FastConfirmationTracker tracker = FastConfirmationTracker.create(false);

    tracker.initialize(store);

    assertThat(tracker.isEnabled()).isFalse();
    assertThat(tracker.getFastConfirmationStore()).isEmpty();
  }

  @Test
  void shouldInitializeStoreFromFinalizedCheckpointWhenEnabled() {
    when(store.getFinalizedCheckpoint()).thenReturn(finalizedCheckpoint);
    final FastConfirmationTracker tracker = FastConfirmationTracker.create(true);

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
}
