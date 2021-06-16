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

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * A fork choice trigger that implements the LMD GHOST Balance Attack mitigation from HF1.
 *
 * @see <a href="https://hackmd.io/nyKbwIluQlWBH6sli4tJlA?view">Implementation plan</a>
 */
class BalanceAttackMitigationForkChoiceTrigger implements ForkChoiceTrigger {

  private final ForkChoice forkChoice;
  private final ForkChoiceRatchet forkChoiceRatchet;

  BalanceAttackMitigationForkChoiceTrigger(final ForkChoice forkChoice) {
    this.forkChoice = forkChoice;
    forkChoiceRatchet = new ForkChoiceRatchet(forkChoice);
  }

  @Override
  public void onSlotStartedWhileSyncing(final UInt64 nodeSlot) {
    // We're technically processing attestations at the end of the previous slot so the fork choice
    // slot needs to be nodeSlot - 1.  Otherwise we wind up deciding every slot is empty immediately
    // and then treating the block as a reorg even if it arrives on time.
    forkChoiceRatchet.scheduleForkChoiceForSlot(nodeSlot.minusMinZero(1));
  }

  @Override
  public void onSlotStarted(final UInt64 nodeSlot) {
    // We're technically processing attestations at the end of the previous slot so the fork choice
    // slot needs to be nodeSlot - 1.  Otherwise we wind up deciding every slot is empty immediately
    // and then treating the block as a reorg even if it arrives on time.
    forkChoiceRatchet.scheduleForkChoiceForSlot(nodeSlot.minusMinZero(1));
  }

  /**
   * Called at the point in the slot when attestations are due. This is also the point where the
   * block for that slot must have been received by.
   *
   * @param slot the slot attestations are due for
   */
  @Override
  public void onAttestationsDueForSlot(final UInt64 slot) {
    forkChoice.onBlocksDueForSlot(slot);
  }

  @Override
  public SafeFuture<Void> prepareForBlockProduction(final UInt64 slot) {
    return forkChoiceRatchet.ensureForkChoiceCompleteForSlot(slot);
  }

  @Override
  public SafeFuture<Void> prepareForAttestationProduction(final UInt64 slot) {
    // We only run fork choice when preparing for blocks. It will already have been done by now.
    return SafeFuture.COMPLETE;
  }
}
