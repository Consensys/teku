/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.spec.config.Constants.RECENT_SEEN_EXECUTION_PAYLOADS_CACHE_SIZE;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.BlockRootAndBuilderIndex;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DefaultExecutionPayloadManager
    implements ExecutionPayloadManager, ReceivedBlockEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final Set<Bytes32> recentSeenExecutionPayloads =
      LimitedSet.createSynchronized(RECENT_SEEN_EXECUTION_PAYLOADS_CACHE_SIZE);

  // pending pool
  private final Map<BlockRootAndBuilderIndex, SignedExecutionPayloadEnvelope>
      pendingExecutionPayloads = LimitedMap.createSynchronizedLRU(32);

  private final Subscribers<FailedPayloadExecutionSubscriber> failedPayloadExecutionSubscribers =
      Subscribers.create(true);

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final ExecutionPayloadGossipValidator executionPayloadGossipValidator;
  private final ForkChoice forkChoice;
  private final ExecutionLayerChannel executionLayer;
  private final ReceivedExecutionPayloadEventsChannel
      receivedExecutionPayloadEventsChannelPublisher;
  private final RecentChainData recentChainData;
  private final Function<SignedExecutionPayloadEnvelope, SafeFuture<Void>>
      executionPayloadPublisher;

  public DefaultExecutionPayloadManager(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final ExecutionPayloadGossipValidator executionPayloadGossipValidator,
      final ForkChoice forkChoice,
      final ExecutionLayerChannel executionLayer,
      final ReceivedExecutionPayloadEventsChannel receivedExecutionPayloadEventsChannelPublisher,
      final RecentChainData recentChainData,
      final Function<SignedExecutionPayloadEnvelope, SafeFuture<Void>> executionPayloadPublisher) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.executionPayloadGossipValidator = executionPayloadGossipValidator;
    this.forkChoice = forkChoice;
    this.executionLayer = executionLayer;
    this.receivedExecutionPayloadEventsChannelPublisher =
        receivedExecutionPayloadEventsChannelPublisher;
    this.recentChainData = recentChainData;
    this.executionPayloadPublisher = executionPayloadPublisher;
  }

  @Override
  public boolean isExecutionPayloadRecentlySeen(final Bytes32 beaconBlockRoot) {
    return recentSeenExecutionPayloads.contains(beaconBlockRoot);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<UInt64> arrivalTimestamp) {
    final SafeFuture<InternalValidationResult> validationResult =
        executionPayloadGossipValidator.validate(signedExecutionPayload);
    validationResult.thenAccept(
        result -> {
          switch (result.code()) {
            case ACCEPT -> {
              receivedExecutionPayloadEventsChannelPublisher.onExecutionPayloadValidated(
                  signedExecutionPayload);
              // cache the seen `beacon_block_root` when the gossip checks pass
              recentSeenExecutionPayloads.add(signedExecutionPayload.getBeaconBlockRoot());
              importExecutionPayload(signedExecutionPayload).finishError(LOG);
            }
            case SAVE_FOR_FUTURE -> {
              if (recentChainData.containsBlock(signedExecutionPayload.getBeaconBlockRoot())) {
                // handles edge case where block was imported while validating the payload
                asyncRunner
                    .runAfterDelay(
                        () ->
                            validateAndImportExecutionPayload(signedExecutionPayload)
                                .thenCompose(r -> publishPayload(r, signedExecutionPayload)),
                        Duration.ofMillis(100))
                    .finishError(LOG);
              } else {
                // import will be triggered when the corresponding block is imported
                pendingExecutionPayloads.put(
                    signedExecutionPayload.getBlockRootAndBuilderIndex(), signedExecutionPayload);
              }
            }
            case REJECT, IGNORE -> {}
          }
        });
    return validationResult;
  }

  @Override
  public SafeFuture<ExecutionPayloadImportResult> importExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    return asyncRunner
        .runAsync(
            () -> forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenPeek(
            result -> {
              if (result.isSuccessful()) {
                LOG.debug(
                    "Successfully imported execution payload {}",
                    signedExecutionPayload::toLogString);
                receivedExecutionPayloadEventsChannelPublisher.onExecutionPayloadImported(
                    signedExecutionPayload, result.isImportedOptimistically());
              } else {
                switch (result.getFailureReason()) {
                  case FAILED_EXECUTION -> {
                    LOG.error(
                        "Unable to import execution payload {}. Execution Client returned an error: {}",
                        signedExecutionPayload::toLogString,
                        () -> result.getFailureCause().map(Throwable::getMessage).orElse(""));
                    failedPayloadExecutionSubscribers.deliver(
                        FailedPayloadExecutionSubscriber::onPayloadExecutionFailed,
                        signedExecutionPayload);
                  }
                  case INTERNAL_ERROR,
                          UNKNOWN_BEACON_BLOCK_ROOT,
                          FAILED_VERIFICATION,
                          FAILED_DATA_AVAILABILITY_CHECK_INVALID,
                          FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE ->
                      logFailedExecutionPayloadImport(signedExecutionPayload, result);
                }
              }
            })
        .exceptionally(
            ex -> {
              final String internalErrorMessage =
                  String.format(
                      "Internal error while importing execution payload: %s. Execution payload content: %s",
                      signedExecutionPayload.toLogString(),
                      signedExecutionPayload.sszSerialize().toHexString());
              LOG.error(internalErrorMessage, ex);
              return ExecutionPayloadImportResult.internalError(ex);
            });
  }

  private void logFailedExecutionPayloadImport(
      final SignedExecutionPayloadEnvelope executionPayload,
      final ExecutionPayloadImportResult importResult) {
    LOG.debug(
        "Unable to import execution payload for reason {}: {}",
        importResult::toLogString,
        executionPayload::toLogString);
  }

  @Override
  public SafeFuture<ExecutionRequests> getParentExecutionRequestsForBlock(
      final UInt64 slot, final Bytes32 parentRoot, final ForkChoicePayloadStatus payloadStatus) {
    if (!payloadStatus.equals(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL)) {
      return SafeFuture.completedFuture(
          SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
              .getExecutionRequestsSchema()
              .getDefault());
    }
    // to avoid querying the EL when unblinding in some cases, we directly query for the blinded
    // execution payload which include the in-memory payloads as well
    return recentChainData
        .retrieveSignedBlindedExecutionPayloadByBlockRoot(parentRoot)
        .thenApply(
            executionPayload ->
                executionPayload
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                String.format(
                                    "Execution Payload is not available for parent root %s during block production for slot %s",
                                    parentRoot, slot)))
                    .getMessage()
                    .getExecutionRequests());
  }

  @Override
  public void subscribeFailedPayloadExecution(final FailedPayloadExecutionSubscriber subscriber) {
    failedPayloadExecutionSubscribers.subscribe(subscriber);
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {}

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    // Process pending execution payloads
    block
        .getMessage()
        .getBody()
        .toVersionGloas()
        .map(BeaconBlockBodyGloas::getSignedExecutionPayloadBid)
        .map(
            bid ->
                new BlockRootAndBuilderIndex(block.getRoot(), bid.getMessage().getBuilderIndex()))
        .map(pendingExecutionPayloads::remove)
        .ifPresent(
            executionPayloadToProcess ->
                validateAndImportExecutionPayload(executionPayloadToProcess)
                    .thenCompose(result -> publishPayload(result, executionPayloadToProcess))
                    .finishError(LOG));
  }

  // publish payload in cases where initial validation wasn't accepted
  private SafeFuture<Void> publishPayload(
      final InternalValidationResult result,
      final SignedExecutionPayloadEnvelope executionPayload) {
    if (result.isAccept()) {
      return executionPayloadPublisher.apply(executionPayload);
    }
    return SafeFuture.COMPLETE;
  }
}
