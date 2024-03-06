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

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
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
    super.processOperationsNoValidation(state, body, indexedAttestationCache);

    safelyProcess(
        () ->
            processDepositReceipts(
                MutableBeaconStateElectra.required(state),
                body.getOptionalExecutionPayload()
                    .flatMap(ExecutionPayload::toVersionElectra)
                    .map(ExecutionPayloadElectra::getDepositReceipts)
                    .orElseThrow(
                        () ->
                            new BlockProcessingException(
                                "Deposit receipts were not found during block processing."))));
  }

  /** Disable former deposit mechanism once all prior deposits are processed * */
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
              state.getEth1DepositIndex().minus(eth1DepositIndexLimit).intValue());

      checkArgument(
          body.getDeposits().size() == expectedDepositCount,
          "process_operations: Verify that outstanding deposits are processed up to the maximum number of deposits");
    } else {
      checkArgument(
          body.getDeposits().isEmpty(),
          "process_operations: Verify that former deposit mechanism has been disabled");
    }
  }

  public void processDepositReceipts(
      final MutableBeaconStateElectra state, final SszList<DepositReceipt> depositReceipts) {
    for (final DepositReceipt depositReceipt : depositReceipts) {
      // process_deposit_receipt
      if (state
          .getDepositReceiptsStartIndex()
          .equals(SpecConfigElectra.UNSET_DEPOSIT_RECEIPTS_START_INDEX)) {
        state.setDepositReceiptsStartIndex(depositReceipt.getIndex());
      }
      applyDeposit(
          state,
          null,
          depositReceipt.getPubkey(),
          depositReceipt.getWithdrawalCredentials(),
          depositReceipt.getAmount(),
          depositReceipt.getSignature(),
          false);
    }
  }
}
