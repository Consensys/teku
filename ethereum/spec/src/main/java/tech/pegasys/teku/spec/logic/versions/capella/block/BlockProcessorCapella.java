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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
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
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
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
    processWithdrawals(genericState, executionPayloadHeader);
    super.executionProcessing(
        genericState, executionPayloadHeader, maybeExecutionPayload, payloadExecutor);
  }

  @Override
  @CheckReturnValue
  protected BlockValidationResult validateBlockPreProcessing(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    return verifyBlsToExecutionChangesPreProcessing(
        preState,
        block
            .getMessage()
            .getBody()
            .getOptionalBlsToExecutionChanges()
            .orElseThrow(
                () ->
                    new BlockProcessingException(
                        "BlsToExecutionChanges was not found during block processing.")),
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
        body.getOptionalBlsToExecutionChanges()
            .orElseThrow(
                () ->
                    new BlockProcessingException(
                        "BlsToExecutionChanges was not found during block processing.")));
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

  @Override
  public void processWithdrawals(
      final MutableBeaconState genericState, final ExecutionPayloadSummary payloadSummary)
      throws BlockProcessingException {
    final MutableBeaconStateCapella state = MutableBeaconStateCapella.required(genericState);
    final SszList<Withdrawal> expectedWithdrawals =
        schemaDefinitionsCapella
            .getExecutionPayloadSchema()
            .getWithdrawalsSchemaRequired()
            .createFromElements(getExpectedWithdrawals(state));

    if (payloadSummary.getOptionalWithdrawalsRoot().isEmpty()
        || !expectedWithdrawals
            .hashTreeRoot()
            .equals(payloadSummary.getOptionalWithdrawalsRoot().get())) {
      throw new BlockProcessingException(
          "Expected "
              + expectedWithdrawals.hashTreeRoot()
              + " withdrawals root, but withdrawals root was "
              + (payloadSummary.getOptionalWithdrawalsRoot().isPresent()
                  ? payloadSummary.getOptionalWithdrawalsRoot().get()
                  : "MISSING"));
    }
    for (int i = 0; i < expectedWithdrawals.size(); i++) {
      final Withdrawal withdrawal = expectedWithdrawals.get(i);
      beaconStateMutators.decreaseBalance(
          state, withdrawal.getValidatorIndex().intValue(), withdrawal.getAmount());
    }

    if (expectedWithdrawals.size() > 0) {
      final Withdrawal latestWithdrawal = expectedWithdrawals.get(expectedWithdrawals.size() - 1);
      final int nextWithdrawalValidatorIndex =
          incrementValidatorIndex(
              latestWithdrawal.getValidatorIndex().intValue(), genericState.getValidators().size());
      state.setNextWithdrawalIndex(latestWithdrawal.getIndex().increment());
      state.setNextWithdrawalValidatorIndex(UInt64.valueOf(nextWithdrawalValidatorIndex));
    }
  }
  // process_withdrawals

  @Override
  public Optional<List<Withdrawal>> getExpectedWithdrawals(final BeaconState preState) {
    return Optional.of(getExpectedWithdrawals(BeaconStateCapella.required(preState)));
  }

  // get_expected_withdrawals
  private List<Withdrawal> getExpectedWithdrawals(final BeaconStateCapella preState) {
    final List<Withdrawal> expectedWithdrawals = new ArrayList<>();
    final WithdrawalSchema withdrawalSchema = schemaDefinitionsCapella.getWithdrawalSchema();
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(preState.getSlot());
    final SszList<Validator> validators = preState.getValidators();
    final SszUInt64List balances = preState.getBalances();
    final int validatorCount = validators.size();
    final int maxWithdrawalsPerPayload = specConfigCapella.getMaxWithdrawalsPerPayload();
    UInt64 withdrawalIndex = preState.getNextWithdrawalIndex();
    int validatorIndex = preState.getNextWithdrawalValidatorIndex().intValue();
    for (int i = 0;
        i < validatorCount && expectedWithdrawals.size() < maxWithdrawalsPerPayload;
        i++) {

      final Validator validator = validators.get(validatorIndex);
      if (predicates.hasEth1WithdrawalCredential(validator)) {
        final UInt64 balance = balances.get(validatorIndex).get();

        if (predicates.isFullyWithdrawableValidatorEth1CredentialsChecked(
            validator, balance, epoch)) {
          expectedWithdrawals.add(
              withdrawalSchema.create(
                  withdrawalIndex,
                  UInt64.valueOf(validatorIndex),
                  new Bytes20(validator.getWithdrawalCredentials().slice(12)),
                  balance));
          withdrawalIndex = withdrawalIndex.increment();
        } else if (predicates.isPartiallyWithdrawableValidatorEth1CredentialsChecked(
            validator, balance)) {
          expectedWithdrawals.add(
              withdrawalSchema.create(
                  withdrawalIndex,
                  UInt64.valueOf(validatorIndex),
                  new Bytes20(validator.getWithdrawalCredentials().slice(12)),
                  balance.minus(specConfig.getMaxEffectiveBalance())));
          withdrawalIndex = withdrawalIndex.increment();
        }
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

  @VisibleForTesting
  BlockValidationResult verifyBlsToExecutionChangesPreProcessing(
      final BeaconState genericState,
      final SszList<SignedBlsToExecutionChange> signedBlsToExecutionChanges,
      final BLSSignatureVerifier signatureVerifier) {

    final Set<UInt64> validatorsSeenInBlock = new HashSet<>();
    for (SignedBlsToExecutionChange signedBlsToExecutionChange : signedBlsToExecutionChanges) {
      final BlsToExecutionChange addressChange = signedBlsToExecutionChange.getMessage();

      if (!validatorsSeenInBlock.add(addressChange.getValidatorIndex())) {
        return BlockValidationResult.failed(
            "Duplicated BlsToExecutionChange for validator " + addressChange.getValidatorIndex());
      }

      final Optional<OperationInvalidReason> operationInvalidReason =
          operationValidator.validateBlsToExecutionChange(
              genericState.getFork(), genericState, addressChange);
      if (operationInvalidReason.isPresent()) {
        return BlockValidationResult.failed(operationInvalidReason.get().describe());
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
