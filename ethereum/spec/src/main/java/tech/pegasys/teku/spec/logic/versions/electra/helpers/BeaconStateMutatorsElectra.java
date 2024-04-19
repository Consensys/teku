/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BeaconStateMutatorsBellatrix;

public class BeaconStateMutatorsElectra extends BeaconStateMutatorsBellatrix {
  private final BeaconStateAccessorsElectra stateAccessorsElectra;

  public BeaconStateMutatorsElectra(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    super(SpecConfigBellatrix.required(specConfig), miscHelpers, beaconStateAccessors);
    this.stateAccessorsElectra = BeaconStateAccessorsElectra.required(beaconStateAccessors);
  }

  /**
   * compute_exit_epoch_and_update_churn
   *
   * @param state mutable beacon state
   * @param exitBalance exit balance
   * @return earliest exit epoch (updated in state)
   */
  public UInt64 computeExitEpochAndUpdateChurn(
      final MutableBeaconStateElectra state, final UInt64 exitBalance) {
    final UInt64 earliestExitEpoch =
        miscHelpers.computeActivationExitEpoch(stateAccessorsElectra.getCurrentEpoch(state));
    final UInt64 perEpochChurn = stateAccessorsElectra.getActivationExitChurnLimit(state);

    if (state.getEarliestExitEpoch().isLessThan(earliestExitEpoch)) {
      state.setEarliestExitEpoch(earliestExitEpoch);
      state.setExitBalanceToConsume(perEpochChurn);
    }
    final UInt64 exitBalanceToConsume = state.getExitBalanceToConsume();
    if (exitBalance.isLessThanOrEqualTo(exitBalanceToConsume)) {
      state.setExitBalanceToConsume(exitBalanceToConsume.minusMinZero(exitBalance));
    } else {
      final UInt64 balanceToProcess = exitBalance.minusMinZero(state.getExitBalanceToConsume());
      final UInt64 additionalEpochs = balanceToProcess.dividedBy(perEpochChurn);
      final UInt64 remainder = balanceToProcess.mod(perEpochChurn);
      state.setEarliestExitEpoch(additionalEpochs.increment());
      state.setExitBalanceToConsume(perEpochChurn.minusMinZero(remainder));
    }
    return state.getEarliestExitEpoch();
  }
}
