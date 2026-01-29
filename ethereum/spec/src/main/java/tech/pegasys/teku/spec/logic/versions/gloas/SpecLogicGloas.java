/*
 * Copyright Consensys Software Inc., 2026
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
import tech.pegasys.teku.spec.logic.common.execution.ExecutionPayloadProcessor;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionRequestsProcessor;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.LightClientUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.ValidatorStatusFactoryAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.spec.logic.versions.capella.operations.validation.OperationValidatorCapella;
import tech.pegasys.teku.spec.logic.versions.electra.execution.ExecutionRequestsProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.util.BlindBlockUtilFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.util.BlockProposalUtilFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.block.BlockProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.execution.ExecutionPayloadProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.execution.ExecutionRequestsProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.forktransition.GloasStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.operations.OperationSignatureVerifierGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.operations.validation.AttestationDataValidatorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.operations.validation.VoluntaryExitValidatorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch.EpochProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.AttestationUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ForkChoiceUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ValidatorsUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class SpecLogicGloas extends AbstractSpecLogic {
  private final Optional<SyncCommitteeUtil> syncCommitteeUtil;
  private final Optional<LightClientUtil> lightClientUtil;
  private final Optional<WithdrawalsHelpers> withdrawalsHelpers;
  private final Optional<ExecutionRequestsProcessor> executionRequestsProcessor;
  private final Optional<ExecutionPayloadProcessor> executionPayloadProcessor;
  private final Optional<ExecutionPayloadProposalUtil> executionPayloadProposalUtil;

  private SpecLogicGloas(
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtilGloas attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryAltair validatorStatusFactory,
      final EpochProcessorGloas epochProcessor,
      final WithdrawalsHelpersGloas withdrawalsHelpers,
      final ExecutionRequestsProcessorElectra executionRequestsProcessor,
      final BlockProcessorGloas blockProcessor,
      final ExecutionPayloadProcessorGloas executionPayloadProcessor,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final BlindBlockUtilFulu blindBlockUtil,
      final SyncCommitteeUtil syncCommitteeUtil,
      final LightClientUtil lightClientUtil,
      final ExecutionPayloadProposalUtil executionPayloadProposalUtil,
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
    this.executionPayloadProcessor = Optional.of(executionPayloadProcessor);
    this.executionPayloadProposalUtil = Optional.of(executionPayloadProposalUtil);
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
        new BeaconStateAccessorsGloas(config, schemaDefinitions, predicates, miscHelpers);
    final BeaconStateMutatorsGloas beaconStateMutators =
        new BeaconStateMutatorsGloas(config, miscHelpers, beaconStateAccessors, schemaDefinitions);

    // Operation validation
    final OperationSignatureVerifierGloas operationSignatureVerifier =
        new OperationSignatureVerifierGloas(miscHelpers, beaconStateAccessors, predicates);

    // Util
    final ValidatorsUtilGloas validatorsUtil =
        new ValidatorsUtilGloas(config, miscHelpers, beaconStateAccessors);
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(
            config, schemaDefinitions, predicates, miscHelpers, beaconStateAccessors);
    final AttestationUtilGloas attestationUtil =
        new AttestationUtilGloas(config, schemaDefinitions, beaconStateAccessors, miscHelpers);
    final AttestationDataValidatorGloas attestationDataValidator =
        new AttestationDataValidatorGloas(config, miscHelpers, beaconStateAccessors);
    final VoluntaryExitValidatorGloas voluntaryExitValidator =
        new VoluntaryExitValidatorGloas(config, predicates, beaconStateAccessors, miscHelpers);
    final OperationValidator operationValidator =
        new OperationValidatorCapella(
            predicates,
            beaconStateAccessors,
            attestationDataValidator,
            attestationUtil,
            voluntaryExitValidator);
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
    final ExecutionRequestsProcessorGloas executionRequestsProcessor =
        new ExecutionRequestsProcessorGloas(
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
    final ExecutionPayloadProcessorGloas executionPayloadProcessor =
        new ExecutionPayloadProcessorGloas(
            config,
            schemaDefinitions,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            executionRequestsDataCodec,
            executionRequestsProcessor);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtilGloas(
            config, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtilFulu(schemaDefinitions, blockProcessor, config.getFuluForkEpoch());

    final BlindBlockUtilFulu blindBlockUtil = new BlindBlockUtilFulu(schemaDefinitions);

    final ExecutionPayloadProposalUtil executionPayloadProposalUtil =
        new ExecutionPayloadProposalUtil(schemaDefinitions, executionPayloadProcessor);

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
        executionPayloadProcessor,
        forkChoiceUtil,
        blockProposalUtil,
        blindBlockUtil,
        syncCommitteeUtil,
        lightClientUtil,
        executionPayloadProposalUtil,
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

  @Override
  public Optional<ExecutionPayloadProcessor> getExecutionPayloadProcessor() {
    return executionPayloadProcessor;
  }

  @Override
  public Optional<ExecutionPayloadProposalUtil> getExecutionPayloadProposalUtil() {
    return executionPayloadProposalUtil;
  }
}
