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

package tech.pegasys.teku.spec.logic.versions.rayonism;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigRayonism;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.StateTransition;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.CommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.rayonism.block.BlockProcessorRayonism;
import tech.pegasys.teku.spec.logic.versions.rayonism.helpers.BeaconStateAccessorsRayonism;
import tech.pegasys.teku.spec.logic.versions.rayonism.helpers.MiscHelpersRayonism;
import tech.pegasys.teku.spec.logic.versions.rayonism.statetransition.StateTransitionRayonism;
import tech.pegasys.teku.spec.logic.versions.rayonism.statetransition.epoch.EpochProcessorRayonism;
import tech.pegasys.teku.spec.logic.versions.rayonism.statetransition.epoch.ValidatorStatusFactoryRayonism;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsRayonism;

public class SpecLogicRayonism extends AbstractSpecLogic {

  private final ExecutionPayloadUtil executionPayloadUtil;

  private SpecLogicRayonism(
      final Predicates predicates,
      final MiscHelpersRayonism miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final CommitteeUtil committeeUtil,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorStatusFactoryRayonism validatorStatusFactory,
      final EpochProcessorRayonism epochProcessor,
      final BlockProcessorRayonism blockProcessor,
      final StateTransition stateTransition,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final ExecutionPayloadUtil executionPayloadUtil) {
    super(
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        committeeUtil,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        validatorStatusFactory,
        epochProcessor,
        blockProcessor,
        stateTransition,
        forkChoiceUtil,
        blockProposalUtil);

    this.executionPayloadUtil = executionPayloadUtil;
  }

  public static SpecLogicRayonism create(
      final SpecConfigRayonism config, final SchemaDefinitionsRayonism schemaDefinitions) {
    // Helpers
    final Predicates predicates = new Predicates();
    final MiscHelpersRayonism miscHelpers = new MiscHelpersRayonism(config);
    final BeaconStateAccessorsRayonism beaconStateAccessors =
        new BeaconStateAccessorsRayonism(config, predicates, miscHelpers);
    final BeaconStateMutators beaconStateMutators =
        new BeaconStateMutators(config, miscHelpers, beaconStateAccessors);

    // Operation validaton
    final AttestationDataStateTransitionValidator attestationValidator =
        new AttestationDataStateTransitionValidator();

    // Util
    final CommitteeUtil committeeUtil = new CommitteeUtil(config);
    final ValidatorsUtil validatorsUtil = new ValidatorsUtil();
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(
            config,
            schemaDefinitions,
            validatorsUtil,
            committeeUtil,
            predicates,
            miscHelpers,
            beaconStateAccessors);
    final AttestationUtil attestationUtil =
        new AttestationUtil(config, beaconStateUtil, beaconStateAccessors, miscHelpers);
    final ValidatorStatusFactoryRayonism validatorStatusFactory =
        new ValidatorStatusFactoryRayonism(
            config, beaconStateUtil, attestationUtil, beaconStateAccessors, predicates);
    final EpochProcessorRayonism epochProcessor =
        new EpochProcessorRayonism(
            config,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            validatorsUtil,
            beaconStateUtil,
            validatorStatusFactory);
    final ExecutionPayloadUtil executionPayloadUtil = new ExecutionPayloadUtil();
    final BlockProcessorRayonism blockProcessor =
        new BlockProcessorRayonism(
            config,
            predicates,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            beaconStateUtil,
            attestationUtil,
            validatorsUtil,
            attestationValidator,
            executionPayloadUtil);
    final StateTransitionRayonism stateTransition =
        StateTransitionRayonism.create(
            config,
            blockProcessor,
            miscHelpers,
            epochProcessor,
            beaconStateUtil,
            beaconStateAccessors);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtil(config, beaconStateUtil, attestationUtil, stateTransition, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtil(schemaDefinitions, stateTransition);

    return new SpecLogicRayonism(
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        committeeUtil,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        validatorStatusFactory,
        epochProcessor,
        blockProcessor,
        stateTransition,
        forkChoiceUtil,
        blockProposalUtil,
        executionPayloadUtil);
  }

  @Override
  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil() {
    return Optional.empty();
  }

  @Override
  public ExecutionPayloadUtil getExecutionPayloadUtil() {
    return executionPayloadUtil;
  }
}
