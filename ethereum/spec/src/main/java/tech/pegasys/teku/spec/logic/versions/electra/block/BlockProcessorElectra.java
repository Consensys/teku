/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.electra.block;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerExit;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.deneb.block.BlockProcessorDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class BlockProcessorElectra extends BlockProcessorDeneb {

  public BlockProcessorElectra(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final MiscHelpersDeneb miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsElectra schemaDefinitions) {
    super(
        specConfig,
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
        SchemaDefinitionsDeneb.required(schemaDefinitions));
  }

  @Override
  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    final MutableBeaconStateElectra electraState = MutableBeaconStateElectra.required(state);
    super.processOperationsNoValidation(state, body, indexedAttestationCache);
    safelyProcess(
        () ->
            processDepositReceipts(
                electraState,
                body.getOptionalExecutionPayload()
                    .flatMap(ExecutionPayload::toVersionElectra)
                    .map(ExecutionPayloadElectra::getDepositReceipts)
                    .orElseThrow(
                        () ->
                            new BlockProcessingException(
                                "Deposit receipts were not found during block processing."))));
  }

  @Override
  protected void verifyOutstandingDepositsAreProcessed(
      final BeaconState state, final BeaconBlockBody body) {
    final UInt64 eth1DepositIndexLimit =
        state
            .getEth1Data()
            .getDepositCount()
            .min(BeaconStateElectra.required(state).getDepositReceiptsStartIndex());

    if (state.getEth1DepositIndex().isLessThan(eth1DepositIndexLimit)) {
      final int expectedDepositCount =
          Math.min(
              specConfig.getMaxDeposits(),
              eth1DepositIndexLimit.minus(state.getEth1DepositIndex()).intValue());

      checkArgument(
          body.getDeposits().size() == expectedDepositCount,
          "process_operations: Verify that outstanding deposits are processed up to the maximum number of deposits");
    } else {
      checkArgument(
          body.getDeposits().isEmpty(),
          "process_operations: Verify that former deposit mechanism has been disabled");
    }
  }

  @Override
  protected void processExecutionLayerExits(
      final MutableBeaconState state,
      final Optional<ExecutionPayload> executionPayload,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    processExecutionLayerExits(
        state, getExecutionLayerExitsFromBlock(executionPayload), validatorExitContextSupplier);
  }

  /*
    Implements process_execution_layer_exit from consensus-spec (EIP-7002)
  */
  @Override
  public void processExecutionLayerExits(
      final MutableBeaconState state,
      final SszList<ExecutionLayerExit> exits,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(state.getSlot());

    exits.forEach(
        exit -> {
          final Optional<Integer> maybeValidatorIndex =
              validatorsUtil.getValidatorIndex(state, exit.getValidatorPublicKey());
          if (maybeValidatorIndex.isEmpty()) {
            return;
          }

          final int validatorIndex = maybeValidatorIndex.get();
          final Validator validator = state.getValidators().get(validatorIndex);

          // Check if validator has eth1 credentials
          boolean isExecutionAddress = predicates.hasEth1WithdrawalCredential(validator);
          if (!isExecutionAddress) {
            return;
          }

          // Check exit source_address matches validator eth1 withdrawal credentials
          final Bytes20 executionAddress =
              new Bytes20(validator.getWithdrawalCredentials().slice(12));
          boolean isCorrectSourceAddress = executionAddress.equals(exit.getSourceAddress());
          if (!isCorrectSourceAddress) {
            return;
          }

          // Check if validator is active
          final boolean isValidatorActive = predicates.isActiveValidator(validator, currentEpoch);
          if (!isValidatorActive) {
            return;
          }

          // Check if validator has already initiated exit
          boolean hasInitiatedExit = !validator.getExitEpoch().equals(FAR_FUTURE_EPOCH);
          if (hasInitiatedExit) {
            return;
          }

          // Check if validator has been active long enough
          final boolean validatorActiveLongEnough =
              currentEpoch.isLessThan(
                  validator.getActivationEpoch().plus(specConfig.getShardCommitteePeriod()));
          if (validatorActiveLongEnough) {
            return;
          }

          // If all conditions are ok, initiate exit
          beaconStateMutators.initiateValidatorExit(
              state, validatorIndex, validatorExitContextSupplier);
        });
  }

  private SszList<ExecutionLayerExit> getExecutionLayerExitsFromBlock(
      final Optional<ExecutionPayload> maybeExecutionPayload) throws BlockProcessingException {
    return maybeExecutionPayload
        .flatMap(ExecutionPayload::toVersionElectra)
        .map(ExecutionPayloadElectra::getExits)
        .orElseThrow(
            () ->
                new BlockProcessingException(
                    "Execution layer exits were not found during block processing."));
  }

  /*
   Implements process_deposit_receipt from consensus-spec (EIP-6110)
  */
  public void processDepositReceipts(
      final MutableBeaconStateElectra state, final SszList<DepositReceipt> depositReceipts) {
    for (DepositReceipt depositReceipt : depositReceipts) {
      // process_deposit_receipt
      if (state
          .getDepositReceiptsStartIndex()
          .equals(SpecConfigElectra.UNSET_DEPOSIT_RECEIPTS_START_INDEX)) {
        state.setDepositReceiptsStartIndex(depositReceipt.getIndex());
      }
      applyDeposit(
          state,
          depositReceipt.getPubkey(),
          depositReceipt.getWithdrawalCredentials(),
          depositReceipt.getAmount(),
          depositReceipt.getSignature(),
          Optional.empty(),
          false);
    }
  }
}
