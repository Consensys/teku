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

import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT_EIP7732;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager.RemoteOrigin;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.block.ReceivedExecutionPayloadEventsChannel;
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

  private final Subscribers<RequiredExecutionPayloadSubscriber>
      requiredExecutionPayloadSubscribers = Subscribers.create(true);

  private static final UInt64 FETCH_DELAY_MILLIS_AFTER_PAYLOAD_PROPAGATION_TIME =
      UInt64.valueOf(1000);

  private final Spec spec;
  private final ExecutionPayloadValidator executionPayloadValidator;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final ExecutionPayloadBroadcaster executionPayloadBroadcaster;
  private final TimeProvider timeProvider;
  private final AsyncRunner asyncRunner;
  private final ReceivedExecutionPayloadEventsChannel
      receivedExecutionPayloadEventsChannelPublisher;
  private final ForkChoice forkChoice;
  private final RecentChainData recentChainData;
  private final ExecutionLayerChannel executionLayerChannel;

  public ExecutionPayloadManager(
      final Spec spec,
      final ExecutionPayloadValidator executionPayloadValidator,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final ExecutionPayloadBroadcaster executionPayloadBroadcaster,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final ReceivedExecutionPayloadEventsChannel receivedExecutionPayloadEventsChannelPublisher,
      final ForkChoice forkChoice,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayerChannel) {
    this.spec = spec;
    this.executionPayloadValidator = executionPayloadValidator;
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.executionPayloadBroadcaster = executionPayloadBroadcaster;
    this.timeProvider = timeProvider;
    this.asyncRunner = asyncRunner;
    this.receivedExecutionPayloadEventsChannelPublisher =
        receivedExecutionPayloadEventsChannelPublisher;
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
              LOG.debug(
                  "Saving execution payload for slot {} and block root {} for future processing",
                  slotAndBlockRoot.getSlot(),
                  slotAndBlockRoot.getBlockRoot());
              recordArrivalTimestamp(executionPayload, arrivalTimestamp);
              savedForFutureExecutionPayloadEnvelopes.put(slotAndBlockRoot, executionPayload);
            }
            case IGNORE, REJECT ->
                LOG.warn(
                    "Execution payload for slot {} and block root {} didn't pass gossip validation: {}",
                    slotAndBlockRoot.getSlot(),
                    slotAndBlockRoot.getBlockRoot(),
                    result);
          }
        });
    return validationResult;
  }

  public void recordArrivalTimestamp(
      final SignedExecutionPayloadEnvelope executionPayload,
      final Optional<UInt64> arrivalTimestamp) {
    arrivalTimestamp.ifPresentOrElse(
        timestamp -> recentChainData.onExecutionPayload(executionPayload, timestamp),
        () -> LOG.error("arrivalTimestamp tracking must be enabled to support Eip7732"));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public void importExecutionPayload(final SignedExecutionPayloadEnvelope executionPayload) {
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
        .thenAccept(
            result -> {
              if (result.isSuccessful()) {
                LOG.debug("Successfully imported {}", executionPayload::toLogString);
                receivedExecutionPayloadEventsChannelPublisher.onExecutionPayloadImported(
                    executionPayload, result.isImportedOptimistically());
                return;
              }
              logFailedExecutionPayloadImport(result, executionPayload);
            })
        .finish(
            err -> {
              final String internalErrorMessage =
                  String.format(
                      "Internal error while importing %s", executionPayload.toLogString());
              LOG.error(internalErrorMessage, err);
            });
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

  public void subscribeRequiredExecutionPayload(
      final RequiredExecutionPayloadSubscriber requiredExecutionPayloadSubscriber) {
    requiredExecutionPayloadSubscribers.subscribe(requiredExecutionPayloadSubscriber);
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {
    // NO-OP
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    if (!spec.atSlot(block.getSlot())
        .getMilestone()
        .isGreaterThanOrEqualTo(SpecMilestone.EIP7732)) {
      return;
    }
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();
    final SignedExecutionPayloadEnvelope executionPayloadDueProcessing =
        savedForFutureExecutionPayloadEnvelopes.remove(slotAndBlockRoot);

    if (executionPayloadDueProcessing != null) {
      // block has been imported, so can apply the execution payload
      executionPayloadValidator
          .validate(executionPayloadDueProcessing)
          .thenAccept(
              result -> {
                if (result.code() == ValidationResultCode.ACCEPT) {
                  // broadcast the payload which was due processing before importing since it's been
                  // gossip validated
                  executionPayloadBroadcaster.broadcast(executionPayloadDueProcessing);
                  importExecutionPayload(executionPayloadDueProcessing);
                } else {
                  LOG.warn(
                      "Execution payload which was saved for future processing for slot {} and block root {} didn't pass gossip validation: {}",
                      slotAndBlockRoot.getSlot(),
                      slotAndBlockRoot.getBlockRoot(),
                      result);
                }
              });
      return;
    }

    final UInt64 currentTime = timeProvider.getTimeInMillis();
    final UInt64 slotStartTime =
        spec.getSlotStartTimeMillis(
            slotAndBlockRoot.getSlot(), recentChainData.getGenesisTimeMillis());
    final UInt64 timeInSlot = currentTime.minusMinZero(slotStartTime);

    // delay fetching after the 2nd interval of the slot
    final UInt64 millisInSlotToFetchExecutionPayload =
        spec.getMillisPerSlot(slotAndBlockRoot.getSlot())
            .dividedBy(INTERVALS_PER_SLOT_EIP7732)
            .times(2)
            .plus(FETCH_DELAY_MILLIS_AFTER_PAYLOAD_PROPAGATION_TIME);

    // fetch required execution payload
    final int fetchDelay = millisInSlotToFetchExecutionPayload.minusMinZero(timeInSlot).intValue();

    asyncRunner.runAfterDelay(
        () -> {
          // already received and imported
          if (validatedExecutionPayloadEnvelopes.containsKey(slotAndBlockRoot)) {
            return;
          }
          requiredExecutionPayloadSubscribers.forEach(
              s -> {
                LOG.debug(
                    "Fetching missing execution payload for slot {} and block root {}",
                    slotAndBlockRoot.getSlot(),
                    slotAndBlockRoot.getBlockRoot());
                s.onRequiredExecutionPayload(block.getRoot());
              });
        },
        Duration.ofMillis(fetchDelay));
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

  private void logFailedExecutionPayloadImport(
      final ExecutionPayloadImportResult result,
      final SignedExecutionPayloadEnvelope executionPayload) {
    LOG.warn(
        "Unable to import {} for reason {}",
        executionPayload::toLogString,
        result::getFailureReason);
  }

  public interface RequiredExecutionPayloadSubscriber {
    void onRequiredExecutionPayload(Bytes32 blockRoot);
  }

  public interface ExecutionPayloadBroadcaster extends VoidReturningChannelInterface {
    void broadcast(SignedExecutionPayloadEnvelope executionPayload);
  }
}
