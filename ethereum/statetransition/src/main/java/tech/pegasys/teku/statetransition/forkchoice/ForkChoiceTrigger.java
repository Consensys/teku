/*
 * Copyright ConsenSys Software Inc., 2022
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

public class ForkChoiceTrigger {

  private final ForkChoiceRatchet forkChoiceRatchet;
  private final ForkChoice forkChoice;

  public ForkChoiceTrigger(final ForkChoice forkChoice) {
    this.forkChoiceRatchet = new ForkChoiceRatchet(forkChoice);
    this.forkChoice = forkChoice;
  }

  public void onSlotStartedWhileSyncing(final UInt64 nodeSlot) {
    forkChoiceRatchet.ensureForkChoiceCompleteForSlot(nodeSlot).join();
  }

  public void onAttestationsDueForSlot(final UInt64 nodeSlot) {
    forkChoiceRatchet.ensureForkChoiceCompleteForSlot(nodeSlot).join();
  }

  public SafeFuture<Void> prepareForBlockProduction(final UInt64 slot) {
    return forkChoice.prepareForBlockProduction(slot);
  }

  public SafeFuture<Void> prepareForAttestationProduction(final UInt64 slot) {
    return forkChoiceRatchet.ensureForkChoiceCompleteForSlot(slot);
  }
}
