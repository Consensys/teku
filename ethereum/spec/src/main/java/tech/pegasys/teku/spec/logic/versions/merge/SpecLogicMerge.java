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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.attestation.AttestationWorthinessChecker;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.attestation.AttestationWorthinessCheckerAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.ValidatorStatusFactoryAltair;
import tech.pegasys.teku.spec.logic.versions.merge.block.BlockProcessorMerge;
import tech.pegasys.teku.spec.logic.versions.merge.forktransition.MergeStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.BeaconStateAccessorsMerge;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.BeaconStateMutatorsMerge;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.MergeTransitionHelpers;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.MiscHelpersMerge;
import tech.pegasys.teku.spec.logic.versions.merge.statetransition.epoch.EpochProcessorMerge;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;

public class SpecLogicMerge extends AbstractSpecLogic {

  private final SpecConfigMerge specConfig;
  private final Optional<SyncCommitteeUtil> syncCommitteeUtil;

  private final Optional<ExecutionPayloadUtil> executionPayloadUtil;
  private final Optional<MergeTransitionHelpers> mergeTransitionHelpers;

  private SpecLogicMerge(
      final SpecConfigMerge specConfig,
      final Predicates predicates,
      final MiscHelpersMerge miscHelpers,
      final BeaconStateAccessorsMerge beaconStateAccessors,
      final BeaconStateMutatorsMerge beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryAltair validatorStatusFactory,
      final EpochProcessorMerge epochProcessor,
      final BlockProcessorMerge blockProcessor,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final SyncCommitteeUtil syncCommitteeUtil,
      final MergeStateUpgrade stateUpgrade,
      final ExecutionPayloadUtil executionPayloadUtil,
      final MergeTransitionHelpers mergeTransitionHelpers) {
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
        Optional.of(stateUpgrade));
    this.specConfig = specConfig;
    this.syncCommitteeUtil = Optional.of(syncCommitteeUtil);
    this.executionPayloadUtil = Optional.of(executionPayloadUtil);
    this.mergeTransitionHelpers = Optional.of(mergeTransitionHelpers);
  }

  public static SpecLogicMerge create(
      final SpecConfigMerge config, final SchemaDefinitionsMerge schemaDefinitions) {
    // Helpers
    final Predicates predicates = new Predicates();
    final MiscHelpersMerge miscHelpers = new MiscHelpersMerge(config);
    final BeaconStateAccessorsMerge beaconStateAccessors =
        new BeaconStateAccessorsMerge(config, predicates, miscHelpers);
    final BeaconStateMutatorsMerge beaconStateMutators =
        new BeaconStateMutatorsMerge(config, miscHelpers, beaconStateAccessors);

    // Operation validaton
    final OperationSignatureVerifier operationSignatureVerifier =
        new OperationSignatureVerifier(miscHelpers, beaconStateAccessors);

    // Util
    final ValidatorsUtil validatorsUtil =
        new ValidatorsUtil(config, miscHelpers, beaconStateAccessors);
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(
            config, schemaDefinitions, predicates, miscHelpers, beaconStateAccessors);
    final AttestationUtil attestationUtil = new AttestationUtil(beaconStateAccessors, miscHelpers);
    final OperationValidator operationValidator =
        OperationValidator.create(
            config, predicates, miscHelpers, beaconStateAccessors, attestationUtil);
    final ValidatorStatusFactoryAltair validatorStatusFactory =
        new ValidatorStatusFactoryAltair(
            config,
            beaconStateUtil,
            attestationUtil,
            predicates,
            miscHelpers,
            beaconStateAccessors);
    final EpochProcessorMerge epochProcessor =
        new EpochProcessorMerge(
            config,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            validatorsUtil,
            beaconStateUtil,
            validatorStatusFactory);
    final BlockProcessorMerge blockProcessor =
        new BlockProcessorMerge(
            config,
            predicates,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            operationSignatureVerifier,
            beaconStateUtil,
            attestationUtil,
            validatorsUtil,
            operationValidator,
            schemaDefinitions);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtil(config, beaconStateAccessors, attestationUtil, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtil(schemaDefinitions, blockProcessor);
    final SyncCommitteeUtil syncCommitteeUtil =
        new SyncCommitteeUtil(
            beaconStateAccessors, validatorsUtil, config, miscHelpers, schemaDefinitions);

    // State upgrade
    final MergeStateUpgrade stateUpgrade =
        new MergeStateUpgrade(config, schemaDefinitions, beaconStateAccessors);

    final MergeTransitionHelpers mergeTransitionHelpers = new MergeTransitionHelpers(config);

    final ExecutionPayloadUtil executionPayloadUtil = new ExecutionPayloadUtil();

    return new SpecLogicMerge(
        config,
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
        syncCommitteeUtil,
        stateUpgrade,
        executionPayloadUtil,
        mergeTransitionHelpers);
  }

  @Override
  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil() {
    return syncCommitteeUtil;
  }

  @Override
  public AttestationWorthinessChecker createAttestationWorthinessChecker(final BeaconState state) {
    final UInt64 currentSlot = state.getSlot();
    final UInt64 startSlot =
        miscHelpers.computeStartSlotAtEpoch(miscHelpers.computeEpochAtSlot(currentSlot));

    final Bytes32 expectedAttestationTarget =
        startSlot.compareTo(currentSlot) == 0 || currentSlot.compareTo(startSlot) <= 0
            ? state.getLatest_block_header().getRoot()
            : beaconStateAccessors.getBlockRootAtSlot(state, startSlot);

    final UInt64 oldestWorthySlotForSourceReward =
        state.getSlot().minusMinZero(specConfig.getSquareRootSlotsPerEpoch());
    return new AttestationWorthinessCheckerAltair(
        expectedAttestationTarget, oldestWorthySlotForSourceReward);
  }

  @Override
  public Optional<ExecutionPayloadUtil> getExecutionPayloadUtil() {
    return executionPayloadUtil;
  }

  @Override
  public Optional<MergeTransitionHelpers> getMergeTransitionHelpers() {
    return mergeTransitionHelpers;
  }
}
