/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProposerWeighting;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ProposerWeightings {
  private final EventThread eventThread;
  private final Spec spec;

  private List<ProposerWeighting> currentProposerWeightings = new ArrayList<>();
  // TODO: Should this be inited based on current time?
  // How much do we need to worry about whether a block received in the first slot after startup
  // gets a boost or not?
  private UInt64 maxSlotWhereBlockOverdue = UInt64.ZERO;

  public ProposerWeightings(final EventThread eventThread, final Spec spec) {
    this.eventThread = eventThread;
    this.spec = spec;
  }

  public void onBlockDueForSlot(final UInt64 slot) {
    eventThread.checkOnEventThread();
    maxSlotWhereBlockOverdue = slot;
  }

  public void onBlockReceived(
      final SignedBeaconBlock block,
      final BeaconState blockSlotState,
      final ForkChoiceStrategy forkChoiceStrategy) {
    eventThread.checkOnEventThread();
    if (isBlockOnTime(block)) {
      final UInt64 priorSlotCommitteeWeight = calculatePriorSlotCommitteeWeight(blockSlotState);
      final UInt64 weight = priorSlotCommitteeWeight.dividedBy(4);
      // weight should be the total weight of attesters to the slot prior to the block slot
      final ProposerWeighting proposerWeighting = new ProposerWeighting(block.getRoot(), weight);
      forkChoiceStrategy.applyProposerWeighting(proposerWeighting);
      currentProposerWeightings.add(proposerWeighting);
    }
  }

  /**
   * Removes all current proposer weightings, returning the list of previously active weightings.
   *
   * @return the list of proposer weightings that were removed.
   */
  public List<ProposerWeighting> clearProposerWeightings() {
    eventThread.checkOnEventThread();
    final List<ProposerWeighting> oldProposerWeightings = this.currentProposerWeightings;
    currentProposerWeightings = new ArrayList<>();
    return oldProposerWeightings;
  }

  private boolean isBlockOnTime(final SignedBeaconBlock block) {
    return block.getSlot().isGreaterThan(maxSlotWhereBlockOverdue);
  }

  private UInt64 calculatePriorSlotCommitteeWeight(final BeaconState blockSlotState) {
    return spec.getBeaconStateUtil(blockSlotState.getSlot())
        .getAttestersTotalEffectiveBalance(
            blockSlotState, blockSlotState.getSlot().minusMinZero(1));
  }
}
