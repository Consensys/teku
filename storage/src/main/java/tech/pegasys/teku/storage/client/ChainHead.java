/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.storage.client;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class ChainHead extends StateAndBlockSummary {
  private final UInt64 forkChoiceSlot;
  private final Spec spec;

  private ChainHead(
      BeaconBlockSummary block, BeaconState state, UInt64 forkChoiceSlot, final Spec spec) {
    super(block, state);
    this.forkChoiceSlot = forkChoiceSlot;
    this.spec = spec;
  }

  public static ChainHead create(
      StateAndBlockSummary blockAndState, UInt64 forkChoiceSlot, final Spec spec) {
    return new ChainHead(
        blockAndState.getBlockSummary(), blockAndState.getState(), forkChoiceSlot, spec);
  }

  /** @return The slot at which the chain head was calculated */
  public UInt64 getForkChoiceSlot() {
    return forkChoiceSlot;
  }

  public UInt64 getForkChoiceEpoch() {
    return spec.computeEpochAtSlot(forkChoiceSlot);
  }

  public SlotAndBlockRoot findCommonAncestor(final ChainHead other) {
    if (getSlot().isZero() || other.getSlot().isZero()) {
      // One fork has no blocks so the only possible common ancestor is genesis.
      if (getSlot().isZero()) {
        return new SlotAndBlockRoot(UInt64.ZERO, getRoot());
      } else if (other.getSlot().isZero()) {
        return new SlotAndBlockRoot(UInt64.ZERO, other.getRoot());
      }
    }
    UInt64 slot = getSlot().min(other.getSlot());
    final UInt64 longestChainSlot = getSlot().max(other.getSlot());
    UInt64 minSlotWithHistoricRoot =
        longestChainSlot
            .max(spec.getSlotsPerHistoricalRoot(slot)) // Avoid underflow
            .minus(spec.getSlotsPerHistoricalRoot(slot));
    while (slot.isGreaterThan(minSlotWithHistoricRoot)) {
      final Bytes32 blockRootAtSlot = getBlockRootAtSlot(slot);
      if (blockRootAtSlot.equals(other.getBlockRootAtSlot(slot))) {
        return new SlotAndBlockRoot(slot, blockRootAtSlot);
      }
      slot = slot.minus(1);
    }
    // Couldn't find a common ancestor in the available block roots so fallback to finalized
    final Checkpoint earliestFinalizedCheckpoint =
        getState()
                .getFinalized_checkpoint()
                .getEpoch()
                .isLessThanOrEqualTo(other.getState().getFinalized_checkpoint().getEpoch())
            ? getState().getFinalized_checkpoint()
            : other.getState().getFinalized_checkpoint();
    return new SlotAndBlockRoot(
        earliestFinalizedCheckpoint.getEpochStartSlot(spec), earliestFinalizedCheckpoint.getRoot());
  }

  private Bytes32 getBlockRootAtSlot(final UInt64 slot) {
    // The block root for the state's own slot is not included in the state so any slot greater than
    // or equal to slot must have the head block root.
    return slot.isGreaterThanOrEqualTo(getSlot())
        ? getRoot()
        : spec.getBlockRootAtSlot(getState(), slot);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    final ChainHead chainHead = (ChainHead) o;
    return Objects.equals(forkChoiceSlot, chainHead.forkChoiceSlot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), forkChoiceSlot);
  }
}
