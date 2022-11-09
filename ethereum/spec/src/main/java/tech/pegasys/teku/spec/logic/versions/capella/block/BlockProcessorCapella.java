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
import javax.annotation.CheckReturnValue;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.constants.WithdrawalPrefixes;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
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
import tech.pegasys.teku.spec.logic.versions.capella.helpers.MiscHelpersCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class BlockProcessorCapella extends BlockProcessorBellatrix {

  private static final Bytes ETH1_WITHDRAWAL_KEY_PREFIX =
      Bytes.concatenate(ETH1_ADDRESS_WITHDRAWAL_PREFIX, Bytes.repeat((byte) 0x00, 11));

  public BlockProcessorCapella(
      final SpecConfigBellatrix specConfig,
      final Predicates predicates,
      final MiscHelpersCapella miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsBellatrix schemaDefinitions) {
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
        schemaDefinitions);
  }

  @Override
  @CheckReturnValue
  protected BlockValidationResult verifyBlockSignatures(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier) {
    return BlockValidationResult.allOf(
        () ->
            super.verifyBlockSignatures(
                preState, block, indexedAttestationCache, signatureVerifier),
        () ->
            verifyBlsToExecutionChanges(
                preState,
                BeaconBlockBodyCapella.required(block.getMessage().getBody())
                    .getBlsToExecutionChanges(),
                signatureVerifier));
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

  @VisibleForTesting
  static Bytes32 getWithdrawalAddressFromEth1Address(final Bytes20 toExecutionAddress) {
    return Bytes32.wrap(
        Bytes.concatenate(ETH1_WITHDRAWAL_KEY_PREFIX, toExecutionAddress.getWrappedBytes()));
  }

  private BlockValidationResult verifyBlsToExecutionChanges(
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
            "Not using BLS withdrawal credentials for validator " + validatorIndex);
      }
      if (withdrawalCredentials.slice(1)
          != Hash.sha256(addressChange.getFromBlsPubkey().toBytesCompressed()).slice(1)) {
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
