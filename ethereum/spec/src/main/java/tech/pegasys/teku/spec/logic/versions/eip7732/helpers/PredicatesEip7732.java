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

package tech.pegasys.teku.spec.logic.versions.eip7732.helpers;

import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.BeaconStateEip7732;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;

public class PredicatesEip7732 extends PredicatesElectra {

  public PredicatesEip7732(final SpecConfig specConfig) {
    super(specConfig);
  }

  public static PredicatesEip7732 required(final Predicates predicates) {
    return predicates
        .toVersionEip7732()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Eip7732 predicates but got "
                        + predicates.getClass().getSimpleName()));
  }

  /**
   * This function returns true if the last committed payload header was fulfilled with a payload,
   * this can only happen when both beacon block and payload were present. This function must be
   * called on a beacon state before processing the execution payload header in the block.
   */
  public boolean isParentBlockFull(final BeaconState state) {
    return BeaconStateBellatrix.required(state)
        .getLatestExecutionPayloadHeader()
        .getBlockHash()
        .equals(BeaconStateEip7732.required(state).getLatestBlockHash());
  }
}
