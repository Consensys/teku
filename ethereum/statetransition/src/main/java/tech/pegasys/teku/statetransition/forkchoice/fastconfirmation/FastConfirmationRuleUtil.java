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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public final class FastConfirmationRuleUtil {

  private FastConfirmationRuleUtil() {}

  public static boolean isStartSlotAtEpoch(final Spec spec, final UInt64 slot) {
    return spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(slot)).equals(slot);
  }

  static FastConfirmationStore updateFastConfirmationVariablesFromInput(
      final FastConfirmationStore fcrStore, final FastConfirmationInput input) {
    return updateFastConfirmationVariables(
        fcrStore,
        input.headRoot(),
        input.greatestUnrealizedJustifiedCheckpoint(),
        input.currentSlotIsEpochStart(),
        input.nextSlotIsEpochStart());
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
