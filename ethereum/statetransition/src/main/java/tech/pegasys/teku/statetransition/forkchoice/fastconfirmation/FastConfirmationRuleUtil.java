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

import java.util.Comparator;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public final class FastConfirmationRuleUtil {

  private FastConfirmationRuleUtil() {}

  public static boolean isStartSlotAtEpoch(final Spec spec, final UInt64 slot) {
    return spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(slot)).equals(slot);
  }

  /**
   * Reconstructs {@code store.unrealized_justified_checkpoint} (the greatest unrealized justified
   * checkpoint) from Teku's per-block checkpoint metadata. This is fork-correct: like the spec's
   * store-level value, it rises from any processed block on any fork.
   */
  static Checkpoint getGreatestUnrealizedJustifiedCheckpoint(final ReadOnlyStore store) {
    return store.getForkChoiceStrategy().getBlockData().stream()
        .map(ProtoNodeData::getCheckpoints)
        .map(BlockCheckpoints::getUnrealizedJustifiedCheckpoint)
        .max(Comparator.comparing(Checkpoint::getEpoch))
        .orElseGet(store::getFinalizedCheckpoint);
  }

  static FastConfirmationStore updateFastConfirmationVariables(
      final FastConfirmationStore fcrStore,
      final Bytes32 currentSlotHead,
      final Checkpoint greatestUnrealizedJustifiedCheckpoint,
      final boolean currentSlotIsEpochStart,
      final boolean nextSlotIsEpochStart) {
    FastConfirmationStore updatedStore =
        new FastConfirmationStore(
            fcrStore.store(),
            fcrStore.confirmedRoot(),
            fcrStore.previousEpochObservedJustifiedCheckpoint(),
            fcrStore.currentEpochObservedJustifiedCheckpoint(),
            fcrStore.previousEpochGreatestUnrealizedCheckpoint(),
            fcrStore.currentSlotHead(),
            currentSlotHead);

    if (nextSlotIsEpochStart) {
      updatedStore =
          new FastConfirmationStore(
              updatedStore.store(),
              updatedStore.confirmedRoot(),
              updatedStore.previousEpochObservedJustifiedCheckpoint(),
              updatedStore.currentEpochObservedJustifiedCheckpoint(),
              greatestUnrealizedJustifiedCheckpoint,
              updatedStore.previousSlotHead(),
              updatedStore.currentSlotHead());
    }

    if (currentSlotIsEpochStart) {
      updatedStore =
          new FastConfirmationStore(
              updatedStore.store(),
              updatedStore.confirmedRoot(),
              updatedStore.currentEpochObservedJustifiedCheckpoint(),
              updatedStore.previousEpochGreatestUnrealizedCheckpoint(),
              updatedStore.previousEpochGreatestUnrealizedCheckpoint(),
              updatedStore.previousSlotHead(),
              updatedStore.currentSlotHead());
    }

    return updatedStore;
  }
}
