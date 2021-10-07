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

package tech.pegasys.teku.spec.logic.versions.merge.block;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Throwables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodyMerge;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.MutableBeaconStateMerge;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel.ConsensusValidationResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.ExecutionEngineSyncing;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.MiscHelpersMerge;

public class BlockProcessorMerge extends BlockProcessorAltair {

  private static final Logger LOG = LogManager.getLogger();

  private final MiscHelpersMerge miscHelpersMerge;
  private final ExecutionPayloadUtil executionPayloadUtil;

  public BlockProcessorMerge(
      final SpecConfigMerge specConfig,
      final Predicates predicates,
      final MiscHelpersMerge miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final ExecutionPayloadUtil executionPayloadUtil) {
    super(
        specConfig.toVersionAltair().orElseThrow(),
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        operationValidator);
    this.miscHelpersMerge = miscHelpers;
    this.executionPayloadUtil = executionPayloadUtil;
  }

  public static BlockProcessorMerge required(BlockProcessor blockProcessor) {
    checkArgument(
        blockProcessor instanceof BlockProcessorMerge,
        "Expected merge block processor but got %s",
        blockProcessor.getClass());
    return (BlockProcessorMerge) blockProcessor;
  }

  @Override
  public BeaconState processAndValidateBlock(
      ExecutionEngineChannel executionEngineChannel,
      SignedBeaconBlock signedBlock,
      BeaconState blockSlotState,
      IndexedAttestationCache indexedAttestationCache)
      throws StateTransitionException {

    BeaconBlockBodyMerge blockBody =
        BeaconBlockBodyMerge.required(signedBlock.getMessage().getBody());
    Bytes32 payloadBlockHash = blockBody.getExecution_payload().getBlock_hash();
    try {
      BeaconState postState =
          super.processAndValidateBlock(
              executionEngineChannel, signedBlock, blockSlotState, indexedAttestationCache);
      if (miscHelpersMerge.isExecutionEnabled(blockSlotState, signedBlock.getMessage())) {
        executionEngineChannel
            .consensusValidated(payloadBlockHash, ConsensusValidationResult.VALID)
            .join();
      }
      return postState;
    } catch (StateTransitionException e) {
      boolean isEESyncing = Throwables.getRootCause(e) instanceof ExecutionEngineSyncing;
      if (!isEESyncing
          && miscHelpersMerge.isExecutionEnabled(blockSlotState, signedBlock.getMessage())) {
        executionEngineChannel
            .consensusValidated(payloadBlockHash, ConsensusValidationResult.INVALID)
            .join();
      }
      throw e;
    }
  }

  @Override
  public void processBlock(
      ExecutionEngineChannel executionEngineChannel,
      MutableBeaconState genericState,
      BeaconBlock block,
      IndexedAttestationCache indexedAttestationCache,
      BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final MutableBeaconStateMerge state = MutableBeaconStateMerge.required(genericState);
    final BeaconBlockBodyMerge blockBody = BeaconBlockBodyMerge.required(block.getBody());

    processBlockHeader(state, block);

    if (miscHelpersMerge.isExecutionEnabled(genericState, block)) {
      processExecutionPayload(executionEngineChannel, state, blockBody.getExecution_payload());
    }

    processRandaoNoValidation(state, block.getBody());
    processEth1Data(state, block.getBody());
    processOperationsNoValidation(state, block.getBody(), indexedAttestationCache);
    processSyncAggregate(state, blockBody.getSyncAggregate(), signatureVerifier);
  }

  @Override
  public void processExecutionPayload(
      ExecutionEngineChannel executionEngineChannel,
      MutableBeaconState genericState,
      ExecutionPayload executionPayload)
      throws BlockProcessingException {
    try {
      final MutableBeaconStateMerge state = MutableBeaconStateMerge.required(genericState);

      if (miscHelpersMerge.isMergeComplete(state)) {
        checkArgument(
            executionPayload
                .getParent_hash()
                .equals(state.getLatest_execution_payload_header().getBlock_hash()),
            "process_execution_payload: Verify that the parent matches");
        checkArgument(
            executionPayload
                .getBlockNumber()
                .equals(state.getLatest_execution_payload_header().getBlockNumber().increment()),
            "process_execution_payload: Verify that the number is consequent");

        checkArgument(
            isValidGasLimit(executionPayload, state.getLatest_execution_payload_header()),
            "process_execution_payload: Verify that gas limit is valid");
      }

      checkArgument(
          executionPayload
              .getRandom()
              .equals(
                  beaconStateAccessors.getRandaoMix(
                      state, beaconStateAccessors.getCurrentEpoch(state))),
          "process_execution_payload: Verify that the random is correct");

      checkArgument(
          executionPayload
              .getTimestamp()
              .equals(miscHelpersMerge.computeTimeAtSlot(state, state.getSlot())),
          "process_execution_payload: Verify that the timestamp is correct");

      ExecutionEngineChannel.ExecutionPayloadStatus executionPayloadStatus =
          executionPayloadUtil.verifyExecutionPayload(executionEngineChannel, executionPayload);

      checkArgument(
          executionPayloadStatus != ExecutionPayloadStatus.INVALID,
          "process_execution_payload: Verify that the payload is valid with respect to execution state transition");

      if (executionPayloadStatus == ExecutionPayloadStatus.SYNCING) {
        throw new ExecutionEngineSyncing();
      }

      state.setLatestExecutionPayloadHeader(
          new ExecutionPayloadHeader(
              executionPayload.getParent_hash(),
              executionPayload.getCoinbase(),
              executionPayload.getState_root(),
              executionPayload.getReceipt_root(),
              executionPayload.getLogs_bloom(),
              executionPayload.getRandom(),
              executionPayload.getBlockNumber(),
              executionPayload.getGas_limit(),
              executionPayload.getGas_used(),
              executionPayload.getTimestamp(),
              executionPayload.getExtraData(),
              executionPayload.getBaseFeePerGas(),
              executionPayload.getBlock_hash(),
              executionPayload.getTransactions().hashTreeRoot()));

    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  private boolean isValidGasLimit(ExecutionPayload payload, ExecutionPayloadHeader parent) {
    final UInt64 parentGasLimit = parent.getGas_limit();

    // Check if the payload used too much gas
    if (payload.getGas_used().isGreaterThan(parentGasLimit)) {
      return false;
    }

    // Check if the payload changed the gas limit too much
    final UInt64 gasLimitMaxDeviation = parentGasLimit.dividedBy(SpecConfig.GAS_LIMIT_DENOMINATOR);
    if (payload.getGas_limit().isGreaterThanOrEqualTo(parentGasLimit.plus(gasLimitMaxDeviation))) {
      return false;
    }
    if (payload.getGas_limit().isLessThanOrEqualTo(parentGasLimit.minus(gasLimitMaxDeviation))) {
      return false;
    }

    // Check if the gas limit is at least the minimum gas limit
    if (payload.getGas_limit().isLessThan(SpecConfig.MIN_GAS_LIMIT)) {
      return false;
    }

    return true;
  }
}
