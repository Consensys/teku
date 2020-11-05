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

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root_at_slot;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

class ChainHead extends StateAndBlockSummary {
  private final UInt64 forkChoiceSlot;

  private ChainHead(BeaconBlockSummary block, BeaconState state, UInt64 forkChoiceSlot) {
    super(block, state);
    this.forkChoiceSlot = forkChoiceSlot;
  }

  public static ChainHead create(StateAndBlockSummary blockAndState, UInt64 forkChoiceSlot) {
    return new ChainHead(blockAndState.getBlockSummary(), blockAndState.getState(), forkChoiceSlot);
  }

  /** @return The slot at which the chain head was calculated */
  public UInt64 getForkChoiceSlot() {
    return forkChoiceSlot;
  }

  public UInt64 findCommonAncestor(final ChainHead other) {
    if (getSlot().equals(UInt64.ZERO) || other.getSlot().equals(UInt64.ZERO)) {
      // One fork has no blocks so the only possible common ancestor is genesis.
      return UInt64.ZERO;
    }
    UInt64 slot = getSlot().min(other.getSlot());
    final UInt64 longestChainSlot = getSlot().max(other.getSlot());
    UInt64 minSlotWithHistoricRoot =
        longestChainSlot
            .max(Constants.SLOTS_PER_HISTORICAL_ROOT) // Avoid underflow
            .minus(Constants.SLOTS_PER_HISTORICAL_ROOT);
    while (slot.isGreaterThan(minSlotWithHistoricRoot)) {
      if (getBlockRootAtSlot(slot).equals(other.getBlockRootAtSlot(slot))) {
        return slot;
      }
      slot = slot.minus(1);
    }
    // Couldn't find a common ancestor in the available block roots so fallback to finalized
    return getState()
        .getFinalized_checkpoint()
        .getEpochStartSlot()
        .min(other.getState().getFinalized_checkpoint().getEpochStartSlot());
  }

  private Bytes32 getBlockRootAtSlot(final UInt64 slot) {
    // The block root for the state's own slot is not included in the state so any slot greater than
    // or equal to slot must have the head block root.
    return slot.isGreaterThanOrEqualTo(getSlot())
        ? getRoot()
        : get_block_root_at_slot(getState(), slot);
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
