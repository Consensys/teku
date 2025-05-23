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

package tech.pegasys.teku.spec.logic.versions.fulu.statetransition.epoch;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.MutableBeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.versions.fulu.ProposerLookahead;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.electra.statetransition.epoch.EpochProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class EpochProcessorFulu extends EpochProcessorElectra {
  private final BeaconStateAccessorsFulu stateAccessorsFulu;
  private final SchemaDefinitions schemaDefinitions;

  public EpochProcessorFulu(
      final SpecConfigFulu specConfig,
      final MiscHelpersFulu miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitions schemaDefinitions,
      final TimeProvider timeProvider) {
    super(
        specConfig,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        schemaDefinitions,
        timeProvider);
    this.stateAccessorsFulu = BeaconStateAccessorsFulu.required(beaconStateAccessors);
    this.schemaDefinitions = schemaDefinitions;
  }

  /** process_proposer_lookahead */
  @Override
  public void processProposerLookahead(final MutableBeaconState state) {
    final MutableBeaconStateFulu stateFulu = MutableBeaconStateFulu.required(state);
    final int slotsPerEpoch = specConfig.getSlotsPerEpoch();
    final int minSeedLookahead = specConfig.getMinSeedLookahead();
    final List<UInt64> proposerIndices =
        stateFulu.getProposerLookahead().asListUnboxed().subList(slotsPerEpoch, (minSeedLookahead+1)*slotsPerEpoch);

    final int lastEpochStart = stateFulu.getProposerLookahead().size() - slotsPerEpoch;

    proposerIndices.addAll(
        stateAccessorsFulu
            .getBeaconProposerIndices(
                state, beaconStateAccessors.getCurrentEpoch(state).plus(minSeedLookahead).plus(1))
            .stream()
            .map(UInt64::valueOf)
            .toList());

    final ProposerLookahead.ProposerLookaheadSchema proposerLookaheadSchema =
        SchemaDefinitionsFulu.required(schemaDefinitions).getProposerLookaheadSchema();

    final SszUInt64List proposerLookaheadList =
            proposerIndices.stream()
            .collect(proposerLookaheadSchema.getLookaheadSchema().collectorUnboxed());

    stateFulu.setProposerLookahead(proposerLookaheadList);
  }
}
