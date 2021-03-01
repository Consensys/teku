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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class ProposerWeightings {
  private final EventThread eventThread;

  private List<ExtraWeight> extraWeightTargetRoots = new ArrayList<>();
  // TODO: Should this be inited based on current time?
  // How much do we need to worry about whether a block received in the first slot after startup
  // gets a boost or not?
  private UInt64 maxSlotWhereBlockOverdue = UInt64.ZERO;

  public ProposerWeightings(final EventThread eventThread) {
    this.eventThread = eventThread;
  }

  public void onBlockDueForSlot(final UInt64 slot) {
    eventThread.checkOnEventThread();
    maxSlotWhereBlockOverdue = slot;
  }

  public void onBlockReceived(
      final SignedBeaconBlock block, final UInt64 priorSlotCommitteeWeight) {
    eventThread.checkOnEventThread();
    if (isBlockOnTime(block)) {
      final UInt64 weight = priorSlotCommitteeWeight.dividedBy(4);
      // weight should be the total weight of attesters to the slot prior to the block slot
      final ExtraWeight extraWeight = new ExtraWeight(block.getRoot(), weight);
      extraWeightTargetRoots.add(extraWeight);
    }
  }

  public void onForkChoiceRunAtSlotStart(final UInt64 slot) {
    eventThread.checkOnEventThread();
    // TODO: Reverse all extra weight target roots
  }

  private boolean isBlockOnTime(final SignedBeaconBlock block) {
    return block.getSlot().isGreaterThan(maxSlotWhereBlockOverdue);
  }

  private static class ExtraWeight {
    private final Bytes32 targetRoot;
    private final UInt64 weight;

    private ExtraWeight(final Bytes32 targetRoot, final UInt64 weight) {
      this.targetRoot = targetRoot;
      this.weight = weight;
    }
  }
}
