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

package tech.pegasys.teku.spec.logic.versions.merge;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
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
import tech.pegasys.teku.spec.logic.versions.merge.block.BlockProcessorMerge;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.BeaconStateAccessorsMerge;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.MiscHelpersMerge;
import tech.pegasys.teku.spec.logic.versions.merge.statetransition.epoch.EpochProcessorMerge;
import tech.pegasys.teku.spec.logic.versions.merge.statetransition.epoch.ValidatorStatusFactoryMerge;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;

public class SpecLogicMerge extends AbstractSpecLogic {

  private final ExecutionPayloadUtil executionPayloadUtil;

  private SpecLogicMerge(
      final Predicates predicates,
      final MiscHelpersMerge miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final CommitteeUtil committeeUtil,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorStatusFactoryMerge validatorStatusFactory,
      final EpochProcessorMerge epochProcessor,
      final BlockProcessorMerge blockProcessor,
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

  public static SpecLogicMerge create(
      final SpecConfigMerge config, final SchemaDefinitionsMerge schemaDefinitions) {
    // Helpers
    final Predicates predicates = new Predicates();
    final MiscHelpersMerge miscHelpers = new MiscHelpersMerge(config, schemaDefinitions);
    final BeaconStateAccessors beaconStateAccessors =
        new BeaconStateAccessorsMerge(config, predicates, miscHelpers);
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
    final ValidatorStatusFactoryMerge validatorStatusFactory =
        new ValidatorStatusFactoryMerge(
            config, beaconStateUtil, attestationUtil, beaconStateAccessors, predicates);
    final EpochProcessorMerge epochProcessor =
        new EpochProcessorMerge(
            config,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            validatorsUtil,
            beaconStateUtil,
            validatorStatusFactory);
    final ExecutionPayloadUtil executionPayloadUtil = new ExecutionPayloadUtil(schemaDefinitions);
    final BlockProcessorMerge blockProcessor =
        new BlockProcessorMerge(
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
    final StateTransition stateTransition =
        StateTransition.create(
            config, blockProcessor, epochProcessor, beaconStateUtil, beaconStateAccessors);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtil(config, beaconStateUtil, attestationUtil, stateTransition, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtil(schemaDefinitions, stateTransition);

    return new SpecLogicMerge(
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
