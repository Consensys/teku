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

package tech.pegasys.teku.spec.logic.versions.gloas;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionRequestsProcessor;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.LightClientUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.ValidatorStatusFactoryAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.spec.logic.versions.capella.operations.validation.OperationValidatorCapella;
import tech.pegasys.teku.spec.logic.versions.deneb.util.ForkChoiceUtilDeneb;
import tech.pegasys.teku.spec.logic.versions.electra.execution.ExecutionRequestsProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.operations.validation.AttestationDataValidatorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.operations.validation.VoluntaryExitValidatorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.util.AttestationUtilElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.util.BlindBlockUtilFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.block.BlockProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.forktransition.GloasStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch.EpochProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class SpecLogicGloas extends AbstractSpecLogic {
  private final Optional<SyncCommitteeUtil> syncCommitteeUtil;
  private final Optional<LightClientUtil> lightClientUtil;
  private final Optional<WithdrawalsHelpers> withdrawalsHelpers;
  private final Optional<ExecutionRequestsProcessor> executionRequestsProcessor;

  private SpecLogicGloas(
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryAltair validatorStatusFactory,
      final EpochProcessorGloas epochProcessor,
      final WithdrawalsHelpersGloas withdrawalsHelpers,
      final ExecutionRequestsProcessorElectra executionRequestsProcessor,
      final BlockProcessorGloas blockProcessor,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final BlindBlockUtilFulu blindBlockUtil,
      final SyncCommitteeUtil syncCommitteeUtil,
      final LightClientUtil lightClientUtil,
      final GloasStateUpgrade stateUpgrade) {
    super(
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        operationValidator,
        validatorStatusFactory,
        epochProcessor,
        blockProcessor,
        forkChoiceUtil,
        blockProposalUtil,
        Optional.of(blindBlockUtil),
        Optional.of(stateUpgrade));
    this.syncCommitteeUtil = Optional.of(syncCommitteeUtil);
    this.lightClientUtil = Optional.of(lightClientUtil);
    this.executionRequestsProcessor = Optional.of(executionRequestsProcessor);
    this.withdrawalsHelpers = Optional.of(withdrawalsHelpers);
  }

  public static SpecLogicGloas create(
      final SpecConfigGloas config,
      final SchemaDefinitionsGloas schemaDefinitions,
      final TimeProvider timeProvider) {
    // Helpers
    final PredicatesGloas predicates = new PredicatesGloas(config);
    final MiscHelpersGloas miscHelpers =
        new MiscHelpersGloas(config, predicates, schemaDefinitions);
    final BeaconStateAccessorsGloas beaconStateAccessors =
        new BeaconStateAccessorsGloas(config, predicates, miscHelpers);
    final BeaconStateMutatorsElectra beaconStateMutators =
        new BeaconStateMutatorsElectra(
            config, miscHelpers, beaconStateAccessors, schemaDefinitions);

    // Operation validation
    final OperationSignatureVerifier operationSignatureVerifier =
        new OperationSignatureVerifier(miscHelpers, beaconStateAccessors);

    // Util
    final ValidatorsUtil validatorsUtil =
        new ValidatorsUtil(config, miscHelpers, beaconStateAccessors);
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(
            config, schemaDefinitions, predicates, miscHelpers, beaconStateAccessors);
    final AttestationUtil attestationUtil =
        new AttestationUtilElectra(config, schemaDefinitions, beaconStateAccessors, miscHelpers);
    final AttestationDataValidator attestationDataValidator =
        new AttestationDataValidatorElectra(config, miscHelpers, beaconStateAccessors);
    final VoluntaryExitValidatorElectra voluntaryExitValidatorElectra =
        new VoluntaryExitValidatorElectra(config, predicates, beaconStateAccessors);
    final OperationValidator operationValidator =
        new OperationValidatorCapella(
            predicates,
            beaconStateAccessors,
            attestationDataValidator,
            attestationUtil,
            voluntaryExitValidatorElectra);
    final ValidatorStatusFactoryAltair validatorStatusFactory =
        new ValidatorStatusFactoryAltair(
            config,
            beaconStateUtil,
            attestationUtil,
            predicates,
            miscHelpers,
            beaconStateAccessors);
    final EpochProcessorGloas epochProcessor =
        new EpochProcessorGloas(
            config,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            validatorsUtil,
            beaconStateUtil,
            validatorStatusFactory,
            schemaDefinitions,
            timeProvider);
    final SyncCommitteeUtil syncCommitteeUtil =
        new SyncCommitteeUtil(
            beaconStateAccessors, validatorsUtil, config, miscHelpers, schemaDefinitions);
    final LightClientUtil lightClientUtil =
        new LightClientUtil(beaconStateAccessors, syncCommitteeUtil, schemaDefinitions);
    final ExecutionRequestsDataCodec executionRequestsDataCodec =
        new ExecutionRequestsDataCodec(schemaDefinitions.getExecutionRequestsSchema());
    final WithdrawalsHelpersGloas withdrawalsHelpers =
        new WithdrawalsHelpersGloas(
            schemaDefinitions, miscHelpers, config, predicates, beaconStateMutators);
    final ExecutionRequestsProcessorElectra executionRequestsProcessor =
        new ExecutionRequestsProcessorElectra(
            schemaDefinitions,
            miscHelpers,
            config,
            predicates,
            validatorsUtil,
            beaconStateMutators,
            beaconStateAccessors);
    final BlockProcessorGloas blockProcessor =
        new BlockProcessorGloas(
            config,
            predicates,
            miscHelpers,
            syncCommitteeUtil,
            beaconStateAccessors,
            beaconStateMutators,
            operationSignatureVerifier,
            beaconStateUtil,
            attestationUtil,
            validatorsUtil,
            operationValidator,
            schemaDefinitions,
            withdrawalsHelpers,
            executionRequestsDataCodec,
            executionRequestsProcessor);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtilDeneb(
            config, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtil(schemaDefinitions, blockProcessor);

    final BlindBlockUtilFulu blindBlockUtil = new BlindBlockUtilFulu(schemaDefinitions);

    // State upgrade
    final GloasStateUpgrade stateUpgrade =
        new GloasStateUpgrade(config, schemaDefinitions, beaconStateAccessors);

    return new SpecLogicGloas(
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        operationValidator,
        validatorStatusFactory,
        epochProcessor,
        withdrawalsHelpers,
        executionRequestsProcessor,
        blockProcessor,
        forkChoiceUtil,
        blockProposalUtil,
        blindBlockUtil,
        syncCommitteeUtil,
        lightClientUtil,
        stateUpgrade);
  }

  @Override
  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil() {
    return syncCommitteeUtil;
  }

  @Override
  public Optional<LightClientUtil> getLightClientUtil() {
    return lightClientUtil;
  }

  @Override
  public Optional<BellatrixTransitionHelpers> getBellatrixTransitionHelpers() {
    return Optional.empty();
  }

  @Override
  public Optional<WithdrawalsHelpers> getWithdrawalsHelpers() {
    return withdrawalsHelpers;
  }

  @Override
  public Optional<ExecutionRequestsProcessor> getExecutionRequestsProcessor() {
    return executionRequestsProcessor;
  }
}
