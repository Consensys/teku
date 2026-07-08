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

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

class FastConfirmationRuleUtilTest {

  private final ReadOnlyStore store = mock(ReadOnlyStore.class);

  @Test
  void shouldUpdateSlotHeadsOnlyForOrdinarySlot() {
    final Bytes32 previousHead = Bytes32.random();
    final Bytes32 currentHead = Bytes32.random();
    final Bytes32 newHead = Bytes32.random();
    final Checkpoint previousObserved = checkpoint(1);
    final Checkpoint currentObserved = checkpoint(2);
    final Checkpoint previousGreatestUnrealized = checkpoint(3);
    final Checkpoint greatestUnrealized = checkpoint(4);
    final FastConfirmationStore fcrStore =
        fastConfirmationStore(
            previousObserved,
            currentObserved,
            previousGreatestUnrealized,
            previousHead,
            currentHead);

    final FastConfirmationStore updatedStore =
        FastConfirmationRuleUtil.updateFastConfirmationVariables(
            fcrStore, newHead, greatestUnrealized, false, false);

    assertThat(updatedStore.previousSlotHead()).isEqualTo(currentHead);
    assertThat(updatedStore.currentSlotHead()).isEqualTo(newHead);
    assertThat(updatedStore.previousEpochObservedJustifiedCheckpoint()).isEqualTo(previousObserved);
    assertThat(updatedStore.currentEpochObservedJustifiedCheckpoint()).isEqualTo(currentObserved);
    assertThat(updatedStore.previousEpochGreatestUnrealizedCheckpoint())
        .isEqualTo(previousGreatestUnrealized);
  }

  @Test
  void shouldCaptureGreatestUnrealizedJustifiedCheckpointOnLastSlotOfEpoch() {
    final Checkpoint previousGreatestUnrealized = checkpoint(3);
    final Checkpoint greatestUnrealized = checkpoint(4);
    final FastConfirmationStore fcrStore =
        fastConfirmationStore(
            checkpoint(1),
            checkpoint(2),
            previousGreatestUnrealized,
            Bytes32.random(),
            Bytes32.random());

    final FastConfirmationStore updatedStore =
        FastConfirmationRuleUtil.updateFastConfirmationVariables(
            fcrStore, Bytes32.random(), greatestUnrealized, false, true);

    assertThat(updatedStore.previousEpochGreatestUnrealizedCheckpoint())
        .isEqualTo(greatestUnrealized);
  }

  @Test
  void shouldRotateObservedJustifiedCheckpointsOnStartSlotOfEpoch() {
    final Checkpoint previousObserved = checkpoint(1);
    final Checkpoint currentObserved = checkpoint(2);
    final Checkpoint previousGreatestUnrealized = checkpoint(3);
    final FastConfirmationStore fcrStore =
        fastConfirmationStore(
            previousObserved,
            currentObserved,
            previousGreatestUnrealized,
            Bytes32.random(),
            Bytes32.random());

    final FastConfirmationStore updatedStore =
        FastConfirmationRuleUtil.updateFastConfirmationVariables(
            fcrStore, Bytes32.random(), checkpoint(4), true, false);

    assertThat(updatedStore.previousEpochObservedJustifiedCheckpoint()).isEqualTo(currentObserved);
    assertThat(updatedStore.currentEpochObservedJustifiedCheckpoint())
        .isEqualTo(previousGreatestUnrealized);
  }

  @Test
  void shouldDetectEpochStartSlots() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final UInt64 epochStart = spec.computeStartSlotAtEpoch(UInt64.ONE);

    assertThat(FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, epochStart)).isTrue();
    assertThat(FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, epochStart.plus(1))).isFalse();
  }

  private FastConfirmationStore fastConfirmationStore(
      final Checkpoint previousObserved,
      final Checkpoint currentObserved,
      final Checkpoint previousGreatestUnrealized,
      final Bytes32 previousHead,
      final Bytes32 currentHead) {
    return new FastConfirmationStore(
        store,
        Bytes32.random(),
        previousObserved,
        currentObserved,
        previousGreatestUnrealized,
        previousHead,
        currentHead);
  }

  private Checkpoint checkpoint(final int epoch) {
    return new Checkpoint(UInt64.valueOf(epoch), Bytes32.random());
  }
}
