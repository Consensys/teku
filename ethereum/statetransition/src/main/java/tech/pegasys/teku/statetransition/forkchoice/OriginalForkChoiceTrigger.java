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
 * Replicates the fork choice behaviour prior to HF1 changes where processHead ran synchronously
 * when attestations were due (or on slot during sync) regardless of whether it had been run for
 * that slot previously.
 */
class OriginalForkChoiceTrigger implements ForkChoiceTrigger {

  private final ForkChoice forkChoice;

  OriginalForkChoiceTrigger(final ForkChoice forkChoice) {
    this.forkChoice = forkChoice;
  }

  @Override
  public void onSlotStartedWhileSyncing(final UInt64 nodeSlot) {
    forkChoice.processHead(nodeSlot).join();
  }

  @Override
  public void onSlotStarted(final UInt64 nodeSlot) {}

  @Override
  public void onAttestationsDueForSlot(final UInt64 nodeSlot) {
    forkChoice.processHead(nodeSlot).join();
  }

  @Override
  public SafeFuture<Void> prepareForBlockProduction(final UInt64 slot) {
    return SafeFuture.COMPLETE;
  }
}
