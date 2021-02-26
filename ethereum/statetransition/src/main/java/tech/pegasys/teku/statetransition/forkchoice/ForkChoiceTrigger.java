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

public interface ForkChoiceTrigger {

  static ForkChoiceTrigger create(
      final ForkChoice forkChoice, final boolean balanceAttackMitigationEnabled) {
    return balanceAttackMitigationEnabled
        ? new BalanceAttackMitigationForkChoiceTrigger(forkChoice)
        : new OriginalForkChoiceTrigger(forkChoice);
  }

  void onSlotStartedWhileSyncing(final UInt64 nodeSlot);

  void onSlotStarted(final UInt64 nodeSlot);

  void onAttestationsDueForSlot(final UInt64 nodeSlot);

  SafeFuture<Void> prepareForBlockProduction(final UInt64 slot);
}
