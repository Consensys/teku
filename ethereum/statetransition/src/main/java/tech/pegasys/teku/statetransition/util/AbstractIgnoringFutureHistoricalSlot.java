/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.util;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

abstract class AbstractIgnoringFutureHistoricalSlot
    implements SlotEventsChannel, FinalizedCheckpointChannel {
  private final Spec spec;

  // Define the range of slots we care about
  private final UInt64 futureSlotTolerance;
  private final UInt64 historicalSlotTolerance;

  private volatile UInt64 currentSlot = UInt64.ZERO;
  private volatile UInt64 latestFinalizedSlot = GENESIS_SLOT;

  public AbstractIgnoringFutureHistoricalSlot(
      final Spec spec, final UInt64 futureSlotTolerance, final UInt64 historicalSlotTolerance) {
    this.spec = spec;
    this.futureSlotTolerance = futureSlotTolerance;
    this.historicalSlotTolerance = historicalSlotTolerance;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot = slot;
    if (currentSlot.mod(historicalSlotTolerance).equals(UInt64.ZERO)) {
      // Purge old items
      prune();
    }
  }

  @VisibleForTesting
  public void prune() {
    final UInt64 slotLimit = latestFinalizedSlot.max(calculateItemAgeLimit());
    prune(slotLimit);
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    this.latestFinalizedSlot = checkpoint.getEpochStartSlot(spec);
  }

  abstract void prune(UInt64 slotLimit);

  protected UInt64 getCurrentSlot() {
    return currentSlot;
  }

  protected UInt64 getLatestFinalizedSlot() {
    return latestFinalizedSlot;
  }

  protected boolean shouldIgnoreItemAtSlot(final UInt64 slot) {
    return isSlotTooOld(slot) || isSlotFromFarFuture(slot);
  }

  private boolean isSlotTooOld(final UInt64 slot) {
    return isSlotFromAFinalizedSlot(slot) || isSlotOutsideOfHistoricalLimit(slot);
  }

  private boolean isSlotFromFarFuture(final UInt64 slot) {
    final UInt64 slotLimit = calculateFutureItemLimit();
    return slot.isGreaterThan(slotLimit);
  }

  private boolean isSlotOutsideOfHistoricalLimit(final UInt64 slot) {
    final UInt64 slotLimit = calculateItemAgeLimit();
    return slot.compareTo(slotLimit) <= 0;
  }

  private boolean isSlotFromAFinalizedSlot(final UInt64 slot) {
    return slot.compareTo(latestFinalizedSlot) <= 0;
  }

  private UInt64 calculateItemAgeLimit() {
    return currentSlot.compareTo(historicalSlotTolerance.plus(UInt64.ONE)) > 0
        ? currentSlot.minus(UInt64.ONE).minus(historicalSlotTolerance)
        : GENESIS_SLOT;
  }

  private UInt64 calculateFutureItemLimit() {
    return currentSlot.plus(futureSlotTolerance);
  }
}
