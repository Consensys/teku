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

package tech.pegasys.teku.ethereum.executionlayer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_CONCURRENT_EB_REQUESTS;
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_CONCURRENT_EE_REQUESTS;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.executionclient.ExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.ThrottlingExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ThrottlingExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.Web3JClient;
import tech.pegasys.teku.ethereum.executionclient.Web3JExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.Web3JExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.BlindedBeaconBlockV1;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderBidV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.SignedMessage;
import tech.pegasys.teku.ethereum.executionclient.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class ExecutionLayerManagerImpl implements ExecutionLayerManager {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionEngineClient executionEngineClient;
  private final Optional<ExecutionBuilderClient> executionBuilderClient;

  private final AtomicBoolean latestBuilderAvailability;

  private final Spec spec;

  private final EventLogger eventLogger;

  public static ExecutionLayerManagerImpl create(
      final Web3JClient engineWeb3JClient,
      final Optional<Web3JClient> builderWeb3JClient,
      final Version version,
      final Spec spec,
      final MetricsSystem metricsSystem) {
    checkNotNull(version);
    return new ExecutionLayerManagerImpl(
        createEngineClient(version, engineWeb3JClient, metricsSystem),
        createBuilderClient(builderWeb3JClient, metricsSystem),
        spec,
        EVENT_LOG);
  }

  private static ExecutionEngineClient createEngineClient(
      final Version version, final Web3JClient web3JClient, final MetricsSystem metricsSystem) {
    LOG.info("Execution Engine version: {}", version);
    if (version != Version.KILNV2) {
      throw new InvalidConfigurationException("Unsupported execution engine version: " + version);
    }
    return new ThrottlingExecutionEngineClient(
        new Web3JExecutionEngineClient(web3JClient), MAXIMUM_CONCURRENT_EE_REQUESTS, metricsSystem);
  }

  private static Optional<ExecutionBuilderClient> createBuilderClient(
      final Optional<Web3JClient> web3JClient, final MetricsSystem metricsSystem) {
    return web3JClient.flatMap(
        client ->
            Optional.of(
                new ThrottlingExecutionBuilderClient(
                    new Web3JExecutionBuilderClient(client),
                    MAXIMUM_CONCURRENT_EB_REQUESTS,
                    metricsSystem)));
  }

  ExecutionLayerManagerImpl(
      final ExecutionEngineClient executionEngineClient,
      final Optional<ExecutionBuilderClient> executionBuilderClient,
      final Spec spec,
      final EventLogger eventLogger) {
    this.executionEngineClient = executionEngineClient;
    this.executionBuilderClient = executionBuilderClient;
    this.latestBuilderAvailability = new AtomicBoolean(executionBuilderClient.isPresent());
    this.spec = spec;
    this.eventLogger = eventLogger;
  }

  @Override
  public void onSlot(UInt64 slot) {
    updateBuilderAvailability();
  }

  @Override
  public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
    LOG.trace("calling eth1GetPowBlock(blockHash={})", blockHash);

    return executionEngineClient
        .getPowBlock(blockHash)
        .thenPeek(
            powBlock -> LOG.trace("eth1GetPowBlock(blockHash={}) -> {}", blockHash, powBlock));
  }

  @Override
  public SafeFuture<PowBlock> eth1GetPowChainHead() {
    LOG.trace("calling eth1GetPowChainHead()");

    return executionEngineClient
        .getPowChainHead()
        .thenPeek(powBlock -> LOG.trace("eth1GetPowChainHead() -> {}", powBlock));
  }

  @Override
  public SafeFuture<tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult>
      engineForkChoiceUpdated(
          final ForkChoiceState forkChoiceState,
          final Optional<PayloadAttributes> payloadAttributes) {

    LOG.trace(
        "calling engineForkChoiceUpdated(forkChoiceState={}, payloadAttributes={})",
        forkChoiceState,
        payloadAttributes);

    return executionEngineClient
        .forkChoiceUpdated(
            ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState),
            PayloadAttributesV1.fromInternalForkChoiceState(payloadAttributes))
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenApply(ForkChoiceUpdatedResult::asInternalExecutionPayload)
        .thenPeek(
            forkChoiceUpdatedResult ->
                LOG.trace(
                    "engineForkChoiceUpdated(forkChoiceState={}, payloadAttributes={}) -> {}",
                    forkChoiceState,
                    payloadAttributes,
                    forkChoiceUpdatedResult));
  }

  @Override
  public SafeFuture<ExecutionPayload> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    LOG.trace(
        "calling engineGetPayload(payloadId={}, slot={})",
        executionPayloadContext.getPayloadId(),
        slot);

    return executionEngineClient
        .getPayload(executionPayloadContext.getPayloadId())
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenCombine(
            SafeFuture.of(
                () ->
                    SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions())
                        .getExecutionPayloadSchema()),
            ExecutionPayloadV1::asInternalExecutionPayload)
        .thenPeek(
            executionPayload ->
                LOG.trace(
                    "engineGetPayload(payloadId={}, slot={}) -> {}",
                    executionPayloadContext.getPayloadId(),
                    slot,
                    executionPayload));
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload) {
    LOG.trace("calling engineNewPayload(executionPayload={})", executionPayload);

    return executionEngineClient
        .newPayload(ExecutionPayloadV1.fromInternalExecutionPayload(executionPayload))
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenApply(PayloadStatusV1::asInternalExecutionPayload)
        .thenPeek(
            payloadStatus ->
                LOG.trace(
                    "engineNewPayload(executionPayload={}) -> {}", executionPayload, payloadStatus))
        .exceptionally(PayloadStatus::failedExecution);
  }

  @Override
  public SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
      TransitionConfiguration transitionConfiguration) {
    LOG.trace(
        "calling engineExchangeTransitionConfiguration(transitionConfiguration={})",
        transitionConfiguration);

    return executionEngineClient
        .exchangeTransitionConfiguration(
            TransitionConfigurationV1.fromInternalTransitionConfiguration(transitionConfiguration))
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenApply(TransitionConfigurationV1::asInternalTransitionConfiguration)
        .thenPeek(
            remoteTransitionConfiguration ->
                LOG.trace(
                    "engineExchangeTransitionConfiguration(transitionConfiguration={}) -> {}",
                    transitionConfiguration,
                    remoteTransitionConfiguration));
  }

  boolean isBuilderAvailable() {
    return latestBuilderAvailability.get();
  }

  @Override
  public SafeFuture<ExecutionPayloadHeader> builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    checkState(executionBuilderClient.isPresent());

    LOG.trace(
        "calling builderGetHeader(slot={}, pubKey={}, parentHash={})",
        slot,
        Bytes48.ZERO,
        executionPayloadContext.getParentHash());

    return executionBuilderClient
        .get()
        .getHeader(slot, Bytes48.ZERO, executionPayloadContext.getParentHash())
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenApply(
            builderBidV1SignedMessage ->
                getExecutionHeaderFromBuilderBid(builderBidV1SignedMessage, slot))
        .thenPeek(
            executionPayloadHeader ->
                LOG.trace(
                    "builderGetHeader(slot={}, pubKey={}, parentHash={}) -> {}",
                    slot,
                    Bytes48.ZERO,
                    executionPayloadContext.getParentHash(),
                    executionPayloadHeader));
  }

  private ExecutionPayloadHeader getExecutionHeaderFromBuilderBid(
      SignedMessage<BuilderBidV1> signedBuilderBid, UInt64 slot) {
    ExecutionPayloadHeaderSchema executionPayloadHeaderSchema =
        SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions())
            .getExecutionPayloadHeaderSchema();
    // validate signature

    return signedBuilderBid
        .getMessage()
        .getHeader()
        .asInternalExecutionPayloadHeader(executionPayloadHeaderSchema);
  }

  @Override
  public SafeFuture<ExecutionPayload> builderGetPayload(
      SignedBeaconBlock signedBlindedBeaconBlock) {
    LOG.trace("calling builderGetPayload(signedBlindedBeaconBlock={})", signedBlindedBeaconBlock);

    checkArgument(
        signedBlindedBeaconBlock.getMessage().getBody().isBlinded(),
        "SignedBeaconBlock must be blind");

    checkState(executionBuilderClient.isPresent());

    return executionBuilderClient
        .get()
        .getPayload(
            new SignedMessage<>(
                new BlindedBeaconBlockV1(signedBlindedBeaconBlock.getMessage()),
                signedBlindedBeaconBlock.getSignature()))
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenCombine(
            SafeFuture.of(
                () ->
                    SchemaDefinitionsBellatrix.required(
                            spec.atSlot(signedBlindedBeaconBlock.getSlot()).getSchemaDefinitions())
                        .getExecutionPayloadSchema()),
            ExecutionPayloadV1::asInternalExecutionPayload)
        .thenPeek(
            executionPayload ->
                LOG.trace(
                    "builderGetPayload(signedBlindedBeaconBlock={}) -> {}",
                    signedBlindedBeaconBlock,
                    executionPayload));
  }

  private static <K> K unwrapResponseOrThrow(Response<K> response) {
    checkArgument(
        response.getErrorMessage() == null,
        "Invalid remote response: %s",
        response.getErrorMessage());
    return checkNotNull(response.getPayload(), "No payload content found");
  }

  private void updateBuilderAvailability() {
    if (executionBuilderClient.isEmpty()) {
      return;
    }
    executionBuilderClient
        .get()
        .status()
        .finish(
            statusResponse -> {
              if (statusResponse.getErrorMessage() != null) {
                markBuilderAsNotAvailable(statusResponse.getErrorMessage());
              } else {
                if (latestBuilderAvailability.compareAndSet(false, true)) {
                  eventLogger.executionBuilderIsBackOnline();
                }
              }
            },
            throwable -> markBuilderAsNotAvailable(throwable.getMessage()));
  }

  private void markBuilderAsNotAvailable(String errorMessage) {
    latestBuilderAvailability.set(false);
    eventLogger.executionBuilderIsOffline(errorMessage);
  }
}
