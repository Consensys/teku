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

package tech.pegasys.teku.spec.logic.versions.bellatrix.block;

import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.MutableBeaconStateBellatrix;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BeaconStateAccessorsBellatrix;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.MiscHelpersBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class BlockProcessorBellatrix extends BlockProcessorAltair {

  private final MiscHelpersBellatrix miscHelpersBellatrix;
  private final SchemaDefinitionsBellatrix schemaDefinitions;

  public BlockProcessorBellatrix(
      final SpecConfigBellatrix specConfig,
      final Predicates predicates,
      final MiscHelpersBellatrix miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsBellatrix beaconStateAccessors,
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
        operationValidator);
    this.miscHelpersBellatrix = miscHelpers;
    this.schemaDefinitions = schemaDefinitions;
  }

  @Override
  public void processBlock(
      final MutableBeaconState genericState,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final OptimisticExecutionPayloadExecutor payloadExecutor)
      throws BlockProcessingException {
    final MutableBeaconStateBellatrix state = MutableBeaconStateBellatrix.required(genericState);
    final BeaconBlockBodyBellatrix blockBody = BeaconBlockBodyBellatrix.required(block.getBody());
    processBlockHeader(state, block);
    if (miscHelpersBellatrix.isExecutionEnabled(genericState, block)) {
      processExecutionPayload(state, blockBody.getExecutionPayload(), payloadExecutor);
    }
    processRandaoNoValidation(state, block.getBody());
    processEth1Data(state, block.getBody());
    processOperationsNoValidation(state, block.getBody(), indexedAttestationCache);
    processSyncAggregate(state, blockBody.getSyncAggregate(), signatureVerifier);
  }

  @Override
  public void processExecutionPayload(
      final MutableBeaconState genericState,
      final ExecutionPayload payload,
      final OptimisticExecutionPayloadExecutor payloadExecutor)
      throws BlockProcessingException {
    final MutableBeaconStateBellatrix state = MutableBeaconStateBellatrix.required(genericState);
    if (miscHelpersBellatrix.isMergeTransitionComplete(state)) {
      if (!payload.getParentHash().equals(state.getLatestExecutionPayloadHeader().getBlockHash())) {
        throw new BlockProcessingException(
            "Execution payload parent hash does not match previous execution payload header");
      }
    }

    if (!beaconStateAccessors
        .getRandaoMix(state, beaconStateAccessors.getCurrentEpoch(state))
        .equals(payload.getRandom())) {
      throw new BlockProcessingException("Execution payload random does not match state randao");
    }

    if (!miscHelpersBellatrix
        .computeTimeAtSlot(state, state.getSlot())
        .equals(payload.getTimestamp())) {
      throw new BlockProcessingException(
          "Execution payload timestamp does not match time for state slot");
    }

    final boolean optimisticallyAccept =
        payloadExecutor.optimisticallyExecute(state.getLatestExecutionPayloadHeader(), payload);
    if (!optimisticallyAccept) {
      throw new BlockProcessingException("Execution payload was not optimistically accepted");
    }

    final ExecutionPayloadHeaderSchema executionPayloadHeaderSchema =
        schemaDefinitions.getExecutionPayloadHeaderSchema();
    state.setLatestExecutionPayloadHeader(
        executionPayloadHeaderSchema.create(
            payload.getParentHash(),
            payload.getFeeRecipient(),
            payload.getStateRoot(),
            payload.getReceiptsRoot(),
            payload.getLogsBloom(),
            payload.getRandom(),
            payload.getBlockNumber(),
            payload.getGasLimit(),
            payload.getGasUsed(),
            payload.getTimestamp(),
            payload.getExtraData(),
            payload.getBaseFeePerGas(),
            payload.getBlockHash(),
            payload.getTransactions().hashTreeRoot()));
  }

  @Override
  public boolean isOptimistic() {
    return true;
  }
}
