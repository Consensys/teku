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

package tech.pegasys.teku.spec.logic.versions.altair.forktransition;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.ssz.primitive.SszByte;
import tech.pegasys.teku.ssz.primitive.SszUInt64;

public class AltairStateUpgrade implements StateUpgrade<BeaconStateAltair> {
  final SpecConfigAltair specConfig;
  final SchemaDefinitionsAltair schemaDefinitions;
  final BeaconStateAccessorsAltair beaconStateAccessors;

  public AltairStateUpgrade(
      final SpecConfigAltair specConfig,
      final SchemaDefinitionsAltair schemaDefinitions,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateAltair upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final int validatorCount = preState.getValidators().size();

    return schemaDefinitions
        .getBeaconStateSchema()
        .createEmpty()
        .updatedAltair(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrent_version(),
                      specConfig.getAltairForkVersion(),
                      epoch));
              state.getPreviousEpochParticipation().setAll(SszByte.ZERO, validatorCount);
              state.getCurrentEpochParticipation().setAll(SszByte.ZERO, validatorCount);
              state.getInactivityScores().setAll(SszUInt64.ZERO, validatorCount);

              // Fill in sync committees
              final SyncCommittee currentSyncCommittee =
                  beaconStateAccessors.getSyncCommittee(state, epoch);
              final SyncCommittee nextSyncCommittee =
                  beaconStateAccessors.getSyncCommittee(
                      state, epoch.plus(specConfig.getEpochsPerSyncCommitteePeriod()));
              state.setCurrentSyncCommittee(currentSyncCommittee);
              state.setNextSyncCommittee(nextSyncCommittee);
            });
  }
}
