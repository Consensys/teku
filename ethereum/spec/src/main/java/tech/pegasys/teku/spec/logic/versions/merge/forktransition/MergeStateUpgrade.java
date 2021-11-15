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

package tech.pegasys.teku.spec.logic.versions.merge.forktransition;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.BeaconStateAccessorsMerge;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;

public class MergeStateUpgrade implements StateUpgrade<BeaconStateMerge> {
  final SpecConfigMerge specConfig;
  final SchemaDefinitionsMerge schemaDefinitions;
  final BeaconStateAccessorsMerge beaconStateAccessors;

  public MergeStateUpgrade(
      final SpecConfigMerge specConfig,
      final SchemaDefinitionsMerge schemaDefinitions,
      final BeaconStateAccessorsMerge beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateMerge upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    BeaconStateAltair preStateAltair = BeaconStateAltair.required(preState);

    return schemaDefinitions
        .getBeaconStateSchema()
        .createEmpty()
        .updatedMerge(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateAltair.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateAltair.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateAltair.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateAltair.getNextSyncCommittee());
              state.setInactivityScores(preStateAltair.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrent_version(),
                      specConfig.getMergeForkVersion(),
                      epoch));

              state.setLatestExecutionPayloadHeader(
                  schemaDefinitions.getExecutionPayloadHeaderSchema().getDefault());
            });
  }
}
