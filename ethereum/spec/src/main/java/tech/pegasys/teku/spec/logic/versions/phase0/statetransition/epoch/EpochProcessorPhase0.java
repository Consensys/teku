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

package tech.pegasys.teku.spec.logic.versions.phase0.statetransition.epoch;

import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.AbstractEpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class EpochProcessorPhase0 extends AbstractEpochProcessor {

  public EpochProcessorPhase0(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitions schemaDefinitions) {
    super(
        specConfig,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        schemaDefinitions);
  }

  @Override
  public RewardAndPenaltyDeltas getRewardAndPenaltyDeltas(
      BeaconState state, ValidatorStatuses validatorStatuses) {
    final RewardsAndPenaltiesCalculatorPhase0 calculator =
        new RewardsAndPenaltiesCalculatorPhase0(
            specConfig, state, validatorStatuses, miscHelpers, beaconStateAccessors);

    return calculator.getDeltas();
  }

  @Override
  public void processParticipationUpdates(MutableBeaconState genericState) {
    // Rotate current/previous epoch attestations
    final MutableBeaconStatePhase0 state = MutableBeaconStatePhase0.required(genericState);
    state.getPreviousEpochAttestations().setAll(state.getCurrentEpochAttestations());
    state.getCurrentEpochAttestations().clear();
  }

  @Override
  public void processHistoricalSummariesUpdate(final MutableBeaconState state) {
    // Nothing to do
  }

  @Override
  public void processSyncCommitteeUpdates(final MutableBeaconState state) {
    // Nothing to do
  }
}
