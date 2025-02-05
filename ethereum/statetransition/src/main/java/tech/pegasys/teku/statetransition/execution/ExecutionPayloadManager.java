/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.execution;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager.RemoteOrigin;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionPayloadManager
    implements ReceivedBlockEventsChannel, FinalizedCheckpointChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<SlotAndBlockRoot, SignedExecutionPayloadEnvelope>
      validatedExecutionPayloadEnvelopes = new ConcurrentHashMap<>();

  private final Map<SlotAndBlockRoot, SignedExecutionPayloadEnvelope>
      savedForFutureExecutionPayloadEnvelopes = new ConcurrentHashMap<>();

  private final Spec spec;
  private final ExecutionPayloadValidator executionPayloadValidator;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final ForkChoice forkChoice;
  private final RecentChainData recentChainData;
  private final ExecutionLayerChannel executionLayerChannel;

  public ExecutionPayloadManager(
      final Spec spec,
      final ExecutionPayloadValidator executionPayloadValidator,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final ForkChoice forkChoice,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayerChannel) {
    this.spec = spec;
    this.executionPayloadValidator = executionPayloadValidator;
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.forkChoice = forkChoice;
    this.recentChainData = recentChainData;
    this.executionLayerChannel = executionLayerChannel;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      final SignedExecutionPayloadEnvelope executionPayload,
      final Optional<UInt64> arrivalTimestamp) {
    final SafeFuture<InternalValidationResult> validationResult =
        executionPayloadValidator.validate(executionPayload);
    // Async import
    validationResult.thenAccept(
        result -> {
          final SlotAndBlockRoot slotAndBlockRoot =
              executionPayload.getMessage().getSlotAndBlockRoot();
          switch (result.code()) {
            case ACCEPT -> {
              recordArrivalTimestamp(executionPayload, arrivalTimestamp);
              validatedExecutionPayloadEnvelopes.put(slotAndBlockRoot, executionPayload);
              importExecutionPayload(executionPayload);
            }
            case SAVE_FOR_FUTURE -> {
              recordArrivalTimestamp(executionPayload, arrivalTimestamp);
              savedForFutureExecutionPayloadEnvelopes.put(slotAndBlockRoot, executionPayload);
            }
            case IGNORE, REJECT -> {}
          }
        });
    return validationResult;
  }

  public Optional<SignedExecutionPayloadEnvelope> getValidatedExecutionPayloadEnvelope(
      final Bytes32 blockRoot) {
    for (final Map.Entry<SlotAndBlockRoot, SignedExecutionPayloadEnvelope> entry :
        validatedExecutionPayloadEnvelopes.entrySet()) {
      if (entry.getKey().getBlockRoot().equals(blockRoot)) {
        return Optional.of(entry.getValue());
      }
    }
    return Optional.empty();
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {
    // NO-OP
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();
    final SignedExecutionPayloadEnvelope executionPayloadDueProcessing =
        savedForFutureExecutionPayloadEnvelopes.remove(slotAndBlockRoot);

    if (executionPayloadDueProcessing == null) {
      return;
    }

    // block has been imported, so can apply the execution payload
    executionPayloadValidator
        .validate(executionPayloadDueProcessing)
        .thenAccept(
            result -> {
              if (result.code() == ValidationResultCode.ACCEPT) {
                importExecutionPayload(executionPayloadDueProcessing);
              } else {
                LOG.warn(
                    "Execution payload which was saved for future processing for slot {} and block root {} didn't pass gossip validation: {}",
                    slotAndBlockRoot.getSlot(),
                    slotAndBlockRoot.getBlockRoot(),
                    result);
              }
            });
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    validatedExecutionPayloadEnvelopes
        .keySet()
        .removeIf(slotAndBlockRoot -> shouldPrune(slotAndBlockRoot, checkpoint));
    savedForFutureExecutionPayloadEnvelopes
        .keySet()
        .removeIf(slotAndBlockRoot -> shouldPrune(slotAndBlockRoot, checkpoint));
  }

  private boolean shouldPrune(
      final SlotAndBlockRoot slotAndBlockRoot, final Checkpoint finalizedCheckpoint) {
    return spec.computeEpochAtSlot(slotAndBlockRoot.getSlot())
        .isLessThanOrEqualTo(finalizedCheckpoint.getEpoch());
  }

  private void recordArrivalTimestamp(
      final SignedExecutionPayloadEnvelope executionPayload,
      final Optional<UInt64> arrivalTimestamp) {
    arrivalTimestamp.ifPresentOrElse(
        timestamp -> recentChainData.onExecutionPayload(executionPayload, timestamp),
        () -> LOG.error("arrivalTimestamp tracking must be enabled to support Eip7732"));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void importExecutionPayload(final SignedExecutionPayloadEnvelope executionPayload) {
    final SlotAndBlockRoot slotAndBlockRoot = executionPayload.getMessage().getSlotAndBlockRoot();
    recentChainData
        .retrieveSignedBlockByRoot(slotAndBlockRoot.getBlockRoot())
        .finish(
            maybeBlock ->
                maybeBlock.ifPresent(
                    block ->
                        blockBlobSidecarsTrackersPool.onNewExecutionPayload(
                            block, executionPayload, Optional.of(RemoteOrigin.GOSSIP))),
            err ->
                LOG.error(
                    "Couldn't retrieve a block for execution payload with slot {} and block root {}",
                    slotAndBlockRoot.getSlot(),
                    slotAndBlockRoot.getBlockRoot()));
    forkChoice
        .onExecutionPayload(executionPayload, executionLayerChannel)
        .finish(err -> LOG.error("Failed to process received execution payload.", err));
  }
}
