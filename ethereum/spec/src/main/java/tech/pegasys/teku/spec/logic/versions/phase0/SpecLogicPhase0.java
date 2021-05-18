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

package tech.pegasys.teku.spec.logic.versions.phase0;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.CommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.phase0.block.BlockProcessorPhase0;
import tech.pegasys.teku.spec.logic.versions.phase0.helpers.BeaconStateAccessorsPhase0;
import tech.pegasys.teku.spec.logic.versions.phase0.statetransition.epoch.EpochProcessorPhase0;
import tech.pegasys.teku.spec.logic.versions.phase0.statetransition.epoch.ValidatorStatusFactoryPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class SpecLogicPhase0 extends AbstractSpecLogic {

  private SpecLogicPhase0(
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final CommitteeUtil committeeUtil,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryPhase0 validatorStatusFactory,
      final EpochProcessorPhase0 epochProcessor,
      final BlockProcessorPhase0 blockProcessor,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil) {
    super(
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        committeeUtil,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        operationValidator,
        validatorStatusFactory,
        epochProcessor,
        blockProcessor,
        forkChoiceUtil,
        blockProposalUtil,
        Optional.empty());
  }

  public static SpecLogicPhase0 create(
      final SpecConfig config, final SchemaDefinitions schemaDefinitions) {
    // Helpers
    final Predicates predicates = new Predicates();
    final MiscHelpers miscHelpers = new MiscHelpers(config);
    final BeaconStateAccessors beaconStateAccessors =
        new BeaconStateAccessorsPhase0(config, predicates, miscHelpers);
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
    final OperationValidator operationValidator =
        OperationValidator.create(beaconStateAccessors, attestationUtil, validatorsUtil);
    final ValidatorStatusFactoryPhase0 validatorStatusFactory =
        new ValidatorStatusFactoryPhase0(
            config, beaconStateUtil, attestationUtil, beaconStateAccessors, predicates);
    final EpochProcessorPhase0 epochProcessor =
        new EpochProcessorPhase0(
            config,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            validatorsUtil,
            beaconStateUtil,
            validatorStatusFactory);
    final BlockProcessorPhase0 blockProcessor =
        new BlockProcessorPhase0(
            config,
            predicates,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            beaconStateUtil,
            attestationUtil,
            validatorsUtil,
            attestationValidator,
            operationValidator);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtil(config, beaconStateUtil, attestationUtil, blockProcessor, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtil(schemaDefinitions, blockProcessor);

    return new SpecLogicPhase0(
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        committeeUtil,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        operationValidator,
        validatorStatusFactory,
        epochProcessor,
        blockProcessor,
        forkChoiceUtil,
        blockProposalUtil);
  }

  @Override
  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil() {
    return Optional.empty();
  }
}
