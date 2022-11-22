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

package tech.pegasys.teku.spec.logic.versions.capella.block;

import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.ETH1_ADDRESS_WITHDRAWAL_PREFIX;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckReturnValue;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.constants.WithdrawalPrefixes;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.MutableBeaconStateCapella;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.BlockProcessorBellatrix;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.MiscHelpersBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;

public class BlockProcessorCapella extends BlockProcessorBellatrix {

  private final SchemaDefinitionsCapella schemaDefinitionsCapella;
  private static final Bytes ETH1_WITHDRAWAL_KEY_PREFIX =
      Bytes.concatenate(ETH1_ADDRESS_WITHDRAWAL_PREFIX, Bytes.repeat((byte) 0x00, 11));
  private final SpecConfigCapella specConfigCapella;

  public BlockProcessorCapella(
      final SpecConfigCapella specConfig,
      final Predicates predicates,
      final MiscHelpersBellatrix miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsCapella schemaDefinitions) {
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
        SchemaDefinitionsBellatrix.required(schemaDefinitions));
    schemaDefinitionsCapella = schemaDefinitions;
    this.specConfigCapella = specConfig;
  }

  @Override
  public void executionProcessing(
      final MutableBeaconState genericState,
      final ExecutionPayloadHeader executionPayloadHeader,
      final Optional<ExecutionPayload> maybeExecutionPayload,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    processWithdrawals(genericState, maybeExecutionPayload);
    super.executionProcessing(
        genericState, executionPayloadHeader, maybeExecutionPayload, payloadExecutor);
  }

  @Override
  @CheckReturnValue
  protected BlockValidationResult validateBlockPreProcessing(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final BLSSignatureVerifier signatureVerifier) {
    return verifyBlsToExecutionChangesPreProcessing(
        preState,
        BeaconBlockBodyCapella.required(block.getMessage().getBody()).getBlsToExecutionChanges(),
        signatureVerifier);
  }

  @Override
  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    super.processOperationsNoValidation(state, body, indexedAttestationCache);

    processBlsToExecutionChangesNoValidation(
        MutableBeaconStateCapella.required(state),
        BeaconBlockBodyCapella.required(body).getBlsToExecutionChanges());
  }

  @Override
  public void processBlsToExecutionChanges(
      final MutableBeaconState state,
      final SszList<SignedBlsToExecutionChange> blsToExecutionChanges)
      throws BlockProcessingException {
    final BlockValidationResult result =
        verifyBlsToExecutionChangesPreProcessing(
            state, blsToExecutionChanges, BLSSignatureVerifier.SIMPLE);
    if (!result.isValid()) {
      throw new BlockProcessingException(result.getFailureReason());
    }
    processBlsToExecutionChangesNoValidation(
        MutableBeaconStateCapella.required(state), blsToExecutionChanges);
  }

  // process_bls_to_execution_change
  public void processBlsToExecutionChangesNoValidation(
      final MutableBeaconStateCapella state,
      final SszList<SignedBlsToExecutionChange> signedBlsToExecutionChanges) {

    for (SignedBlsToExecutionChange signedBlsToExecutionChange : signedBlsToExecutionChanges) {
      BlsToExecutionChange addressChange = signedBlsToExecutionChange.getMessage();
      final int validatorIndex = addressChange.getValidatorIndex().intValue();
      Validator validator = state.getValidators().get(validatorIndex);
      state
          .getValidators()
          .set(
              validatorIndex,
              validator.withWithdrawalCredentials(
                  getWithdrawalAddressFromEth1Address(addressChange.getToExecutionAddress())));
    }
  }

  public void processWithdrawals(
      final MutableBeaconState genericState, final Optional<ExecutionPayload> maybePayload)
      throws BlockProcessingException {
    final ExecutionPayload payload =
        maybePayload.orElseThrow(
            () ->
                new BlockProcessingException(
                    "ExecutionPayload was not found during block processing."));
    processWithdrawals(genericState, payload);
  }

  @Override
  public void processWithdrawals(
      final MutableBeaconState genericState, final ExecutionPayload payload)
      throws BlockProcessingException {
    final MutableBeaconStateCapella state = MutableBeaconStateCapella.required(genericState);
    final ExecutionPayloadCapella executionPayloadCapella =
        ExecutionPayloadCapella.required(payload);
    final SszList<Withdrawal> payloadWithdrawals = executionPayloadCapella.getWithdrawals();
    final List<Withdrawal> expectedWithdrawals = getExpectedWithdrawals(state);
    if (expectedWithdrawals.size() != payloadWithdrawals.size()) {
      throw new BlockProcessingException(
          "Expected "
              + expectedWithdrawals.size()
              + " withdrawals, but payload contained "
              + payloadWithdrawals.size());
    }
    for (int i = 0; i < expectedWithdrawals.size(); i++) {
      final Withdrawal withdrawal = payloadWithdrawals.get(i);
      if (!expectedWithdrawals.get(i).equals(withdrawal)) {
        throw new BlockProcessingException(
            "Withdrawal "
                + withdrawal.getIndex()
                + " does not match expected withdrawal "
                + expectedWithdrawals.get(i).getIndex());
      }
      beaconStateMutators.decreaseBalance(
          state, withdrawal.getValidatorIndex().intValue(), withdrawal.getAmount());
    }

    if (payloadWithdrawals.size() > 0) {
      final Withdrawal latestWithdrawal = payloadWithdrawals.get(payloadWithdrawals.size() - 1);
      final int nextWithdrawalValidatorIndex =
          incrementValidatorIndex(
              latestWithdrawal.getValidatorIndex().intValue(), genericState.getValidators().size());
      state.setNextWithdrawalIndex(latestWithdrawal.getIndex().increment());
      state.setNextWithdrawalValidatorIndex(UInt64.valueOf(nextWithdrawalValidatorIndex));
    }
  }
  // process_withdrawals

  // get_expected_withdrawals
  private List<Withdrawal> getExpectedWithdrawals(final BeaconStateCapella preState) {
    final List<Withdrawal> expectedWithdrawals = new ArrayList<>();
    final WithdrawalSchema withdrawalSchema = schemaDefinitionsCapella.getWithdrawalSchema();
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(preState.getSlot());
    final int validatorCount = preState.getValidators().size();
    UInt64 withdrawalIndex = preState.getNextWithdrawalIndex();
    int validatorIndex = preState.getNextWithdrawalValidatorIndex().intValue();
    for (int i = 0;
        i < validatorCount
            && expectedWithdrawals.size() < specConfigCapella.getMaxWithdrawalsPerPayload();
        i++) {
      final Validator validator = preState.getValidators().get(validatorIndex);
      final UInt64 balance = preState.getBalances().get(validatorIndex).get();
      if (predicates.isFullyWithdrawableValidator(validator, balance, epoch)) {
        expectedWithdrawals.add(
            withdrawalSchema.create(
                withdrawalIndex,
                UInt64.valueOf(validatorIndex),
                new Bytes20(validator.getWithdrawalCredentials().slice(12)),
                balance));
        withdrawalIndex = withdrawalIndex.increment();
      } else if (predicates.isPartiallyWithdrawableValidator(validator, balance)) {
        expectedWithdrawals.add(
            withdrawalSchema.create(
                withdrawalIndex,
                UInt64.valueOf(validatorIndex),
                new Bytes20(validator.getWithdrawalCredentials().slice(12)),
                balance.minus(specConfig.getMaxEffectiveBalance())));
        withdrawalIndex = withdrawalIndex.increment();
      }

      validatorIndex = incrementValidatorIndex(validatorIndex, validatorCount);
    }

    return expectedWithdrawals;
  }

  @VisibleForTesting
  static Bytes32 getWithdrawalAddressFromEth1Address(final Bytes20 toExecutionAddress) {
    return Bytes32.wrap(
        Bytes.concatenate(ETH1_WITHDRAWAL_KEY_PREFIX, toExecutionAddress.getWrappedBytes()));
  }

  @VisibleForTesting
  static int incrementValidatorIndex(final int validatorIndex, final int validatorCount) {
    return (validatorIndex + 1) % validatorCount;
  }

  private BlockValidationResult verifyBlsToExecutionChangesPreProcessing(
      final BeaconState genericState,
      final SszList<SignedBlsToExecutionChange> signedBlsToExecutionChanges,
      final BLSSignatureVerifier signatureVerifier) {
    for (SignedBlsToExecutionChange signedBlsToExecutionChange : signedBlsToExecutionChanges) {
      final BlsToExecutionChange addressChange = signedBlsToExecutionChange.getMessage();
      final int validatorIndex = addressChange.getValidatorIndex().intValue();
      if (genericState.getValidators().size() <= validatorIndex) {
        return BlockValidationResult.failed("Validator index invalid: " + validatorIndex);
      }
      final Bytes32 withdrawalCredentials =
          genericState.getValidators().get(validatorIndex).getWithdrawalCredentials();
      if (withdrawalCredentials.get(0) != WithdrawalPrefixes.BLS_WITHDRAWAL_PREFIX.get(0)) {
        return BlockValidationResult.failed(
            "Not using BLS withdrawal credentials for validator "
                + validatorIndex
                + " Credentials: "
                + withdrawalCredentials);
      }
      if (!withdrawalCredentials
          .slice(1)
          .equals(Hash.sha256(addressChange.getFromBlsPubkey().toBytesCompressed()).slice(1))) {
        return BlockValidationResult.failed(
            "Validator "
                + validatorIndex
                + " public key does not match withdrawal credentials: "
                + addressChange.getFromBlsPubkey());
      }
      boolean signatureValid =
          operationSignatureVerifier.verifyBlsToExecutionChangeSignature(
              genericState.getFork(), genericState, signedBlsToExecutionChange, signatureVerifier);
      if (!signatureValid) {
        return BlockValidationResult.failed(
            "BlsToExecutionChange signature is invalid: " + signedBlsToExecutionChange);
      }
    }
    return BlockValidationResult.SUCCESSFUL;
  }
}
