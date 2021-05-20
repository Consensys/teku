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

package tech.pegasys.teku.spec.logic.versions.altair;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
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
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.altair.forktransition.AltairStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateMutatorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.EpochProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.ValidatorStatusFactoryAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class SpecLogicAltair extends AbstractSpecLogic {

  private final Optional<SyncCommitteeUtil> syncCommitteeUtil;

  private SpecLogicAltair(
      final Predicates predicates,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final CommitteeUtil committeeUtil,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryAltair validatorStatusFactory,
      final EpochProcessorAltair epochProcessor,
      final BlockProcessorAltair blockProcessor,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final SyncCommitteeUtil syncCommitteeUtil,
      final AltairStateUpgrade stateUpgrade) {
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
        Optional.of(stateUpgrade));
    this.syncCommitteeUtil = Optional.of(syncCommitteeUtil);
  }

  public static SpecLogicAltair create(
      final SpecConfigAltair config, final SchemaDefinitionsAltair schemaDefinitions) {
    // Helpers
    final Predicates predicates = new Predicates();
    final MiscHelpersAltair miscHelpers = new MiscHelpersAltair(config);
    final BeaconStateAccessorsAltair beaconStateAccessors =
        new BeaconStateAccessorsAltair(config, predicates, miscHelpers);
    final BeaconStateMutatorsAltair beaconStateMutators =
        new BeaconStateMutatorsAltair(config, miscHelpers, beaconStateAccessors);

    // Operation validaton
    final AttestationDataStateTransitionValidator attestationValidator =
        new AttestationDataStateTransitionValidator(config, miscHelpers, beaconStateAccessors);

    // Util
    final CommitteeUtil committeeUtil = new CommitteeUtil(config);
    final ValidatorsUtil validatorsUtil = new ValidatorsUtil();
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(
            config,
            schemaDefinitions,
            committeeUtil,
            predicates,
            miscHelpers,
            beaconStateAccessors);
    final AttestationUtil attestationUtil =
        new AttestationUtil(config, beaconStateUtil, beaconStateAccessors, miscHelpers);
    final OperationValidator operationValidator =
        OperationValidator.create(beaconStateAccessors, attestationUtil, validatorsUtil);
    final ValidatorStatusFactoryAltair validatorStatusFactory =
        new ValidatorStatusFactoryAltair(
            config,
            beaconStateUtil,
            attestationUtil,
            predicates,
            miscHelpers,
            beaconStateAccessors);
    final EpochProcessorAltair epochProcessor =
        new EpochProcessorAltair(
            config,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            validatorsUtil,
            beaconStateUtil,
            validatorStatusFactory);
    final BlockProcessorAltair blockProcessor =
        new BlockProcessorAltair(
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
        new ForkChoiceUtil(
            config, beaconStateAccessors, attestationUtil, blockProcessor, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtil(schemaDefinitions, blockProcessor);
    final SyncCommitteeUtil syncCommitteeUtil =
        new SyncCommitteeUtil(
            beaconStateAccessors,
            beaconStateUtil,
            validatorsUtil,
            config,
            miscHelpers,
            schemaDefinitions);

    // State upgrade
    final AltairStateUpgrade stateUpgrade =
        new AltairStateUpgrade(
            config, schemaDefinitions, beaconStateAccessors, attestationUtil, miscHelpers);

    return new SpecLogicAltair(
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
        syncCommitteeUtil,
        stateUpgrade);
  }

  @Override
  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil() {
    return syncCommitteeUtil;
  }
}
