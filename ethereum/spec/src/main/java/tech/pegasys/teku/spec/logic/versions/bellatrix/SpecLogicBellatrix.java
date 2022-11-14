/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.bellatrix;

import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.attestation.AttestationWorthinessChecker;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlindBlockUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.SpecLogicAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.ValidatorStatusFactoryAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.BlockProcessorBellatrix;
import tech.pegasys.teku.spec.logic.versions.bellatrix.forktransition.BellatrixStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BeaconStateMutatorsBellatrix;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.MiscHelpersBellatrix;
import tech.pegasys.teku.spec.logic.versions.bellatrix.statetransition.epoch.EpochProcessorBellatrix;
import tech.pegasys.teku.spec.logic.versions.bellatrix.util.BlindBlockUtilBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class SpecLogicBellatrix extends AbstractSpecLogic {
  private final Optional<SyncCommitteeUtil> syncCommitteeUtil;

  private final Function<BeaconState, AttestationWorthinessChecker>
      attestationWorthinessCheckerCreator;

  private final Optional<BellatrixTransitionHelpers> bellatrixTransitionHelpers;

  private SpecLogicBellatrix(
      final Predicates predicates,
      final MiscHelpersBellatrix miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutatorsBellatrix beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final OperationValidator operationValidator,
      final ValidatorStatusFactoryAltair validatorStatusFactory,
      final EpochProcessorBellatrix epochProcessor,
      final BlockProcessorBellatrix blockProcessor,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil,
      final BlindBlockUtil blindBlockUtil,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BellatrixStateUpgrade stateUpgrade,
      final Function<BeaconState, AttestationWorthinessChecker> attestationWorthinessCheckerCreator,
      final BellatrixTransitionHelpers bellatrixTransitionHelpers) {
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
    this.attestationWorthinessCheckerCreator = attestationWorthinessCheckerCreator;
    this.bellatrixTransitionHelpers = Optional.of(bellatrixTransitionHelpers);
  }

  public static SpecLogicBellatrix create(
      final SpecConfigBellatrix config, final SchemaDefinitionsBellatrix schemaDefinitions) {
    // Helpers
    final Predicates predicates = new Predicates(config);
    final MiscHelpersBellatrix miscHelpers = new MiscHelpersBellatrix(config);
    final BeaconStateAccessorsAltair beaconStateAccessors =
        new BeaconStateAccessorsAltair(config, predicates, miscHelpers);
    final BeaconStateMutatorsBellatrix beaconStateMutators =
        new BeaconStateMutatorsBellatrix(config, miscHelpers, beaconStateAccessors);

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
        new AttestationUtil(schemaDefinitions, beaconStateAccessors, miscHelpers);
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
    final EpochProcessorBellatrix epochProcessor =
        new EpochProcessorBellatrix(
            config,
            miscHelpers,
            beaconStateAccessors,
            beaconStateMutators,
            validatorsUtil,
            beaconStateUtil,
            validatorStatusFactory,
            schemaDefinitions);
    final SyncCommitteeUtil syncCommitteeUtil =
        new SyncCommitteeUtil(
            beaconStateAccessors, validatorsUtil, config, miscHelpers, schemaDefinitions);
    final BlockProcessorBellatrix blockProcessor =
        new BlockProcessorBellatrix(
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
            schemaDefinitions);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtil(
            config, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtil(schemaDefinitions, blockProcessor);

    final BlindBlockUtilBellatrix blindBlockUtil = new BlindBlockUtilBellatrix(schemaDefinitions);

    final Function<BeaconState, AttestationWorthinessChecker> attestationWorthinessCheckerCreator =
        SpecLogicAltair.attestationWorthinessCheckerCreator(
            miscHelpers, config, beaconStateAccessors);

    // State upgrade
    final BellatrixStateUpgrade stateUpgrade =
        new BellatrixStateUpgrade(config, schemaDefinitions, beaconStateAccessors);

    final BellatrixTransitionHelpers bellatrixTransitionHelpers =
        new BellatrixTransitionHelpers(config, miscHelpers);

    return new SpecLogicBellatrix(
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
        blindBlockUtil,
        syncCommitteeUtil,
        stateUpgrade,
        attestationWorthinessCheckerCreator,
        bellatrixTransitionHelpers);
  }

  @Override
  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil() {
    return syncCommitteeUtil;
  }

  @Override
  public AttestationWorthinessChecker createAttestationWorthinessChecker(final BeaconState state) {
    return attestationWorthinessCheckerCreator.apply(state);
  }

  @Override
  public Optional<BellatrixTransitionHelpers> getBellatrixTransitionHelpers() {
    return bellatrixTransitionHelpers;
  }
}
