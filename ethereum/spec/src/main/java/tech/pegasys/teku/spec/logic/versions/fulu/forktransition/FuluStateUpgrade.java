/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.versions.fulu.forktransition;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;

public class FuluStateUpgrade implements StateUpgrade<BeaconStateElectra> {

  private final SpecConfigFulu specConfig;
  private final BeaconStateAccessorsElectra beaconStateAccessors;

  public FuluStateUpgrade(
      final SpecConfigFulu specConfig, final BeaconStateAccessorsElectra beaconStateAccessors) {
    this.specConfig = specConfig;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateElectra upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateElectra preStateElectra = BeaconStateElectra.required(preState);
    return preStateElectra.updatedElectra(
        state ->
            state.setFork(
                new Fork(
                    preState.getFork().getCurrentVersion(),
                    specConfig.getFuluForkVersion(),
                    epoch)));
  }
}
