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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionengine.Web3JClient;
import tech.pegasys.teku.ethereum.executionengine.schema.Response;
import tech.pegasys.teku.ethereum.executionlayer.client.ExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionlayer.client.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionlayer.client.Web3JExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionlayer.client.Web3JExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadHeaderV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionengine.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;
import tech.pegasys.teku.spec.executionengine.PayloadStatus;
import tech.pegasys.teku.spec.executionengine.TransitionConfiguration;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class ExecutionLayerChannelImpl implements ExecutionLayerChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionEngineClient executionEngineClient;

  @SuppressWarnings("UnusedVariable") // will be removed in upcoming PR
  private final Optional<ExecutionBuilderClient> executionBuilderClient;

  private final Spec spec;

  public static ExecutionLayerChannelImpl create(
      final Web3JClient engineWeb3JClient,
      final Optional<Web3JClient> builderWeb3JClient,
      final Version version,
      final Spec spec) {
    checkNotNull(version);
    return new ExecutionLayerChannelImpl(
        createEngineClient(version, engineWeb3JClient),
        createBuilderClient(builderWeb3JClient),
        spec);
  }

  private static ExecutionEngineClient createEngineClient(
      final Version version, final Web3JClient web3JClient) {
    LOG.info("Execution Engine version: {}", version);
    if (version != Version.KILNV2) {
      throw new InvalidConfigurationException("Unsupported execution engine version: " + version);
    }
    return new Web3JExecutionEngineClient(web3JClient);
  }

  private static Optional<ExecutionBuilderClient> createBuilderClient(
      final Optional<Web3JClient> web3JClient) {
    return web3JClient.flatMap(client -> Optional.of(new Web3JExecutionBuilderClient(client)));
  }

  private ExecutionLayerChannelImpl(
      final ExecutionEngineClient executionEngineClient,
      final Optional<ExecutionBuilderClient> executionBuilderClient,
      Spec spec) {
    this.spec = spec;
    this.executionEngineClient = executionEngineClient;
    this.executionBuilderClient = executionBuilderClient;
  }

  private static <K> K unwrapResponseOrThrow(Response<K> response) {
    checkArgument(
        response.getErrorMessage() == null,
        "Invalid remote response: %s",
        response.getErrorMessage());
    return checkNotNull(response.getPayload(), "No payload content found");
  }

  @Override
  public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
    LOG.trace("calling getPowBlock(blockHash={})", blockHash);

    return executionEngineClient
        .getPowBlock(blockHash)
        .thenPeek(powBlock -> LOG.trace("getPowBlock(blockHash={}) -> {}", blockHash, powBlock));
  }

  @Override
  public SafeFuture<PowBlock> eth1GetPowChainHead() {
    LOG.trace("calling getPowChainHead()");

    return executionEngineClient
        .getPowChainHead()
        .thenPeek(powBlock -> LOG.trace("getPowChainHead() -> {}", powBlock));
  }

  @Override
  public SafeFuture<tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult>
      engineForkChoiceUpdated(
          final ForkChoiceState forkChoiceState,
          final Optional<PayloadAttributes> payloadAttributes) {

    LOG.trace(
        "calling forkChoiceUpdated(forkChoiceState={}, payloadAttributes={})",
        forkChoiceState,
        payloadAttributes);

    return executionEngineClient
        .forkChoiceUpdated(
            ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState),
            PayloadAttributesV1.fromInternalForkChoiceState(payloadAttributes))
        .thenApply(ExecutionLayerChannelImpl::unwrapResponseOrThrow)
        .thenApply(ForkChoiceUpdatedResult::asInternalExecutionPayload)
        .thenPeek(
            forkChoiceUpdatedResult ->
                LOG.trace(
                    "forkChoiceUpdated(forkChoiceState={}, payloadAttributes={}) -> {}",
                    forkChoiceState,
                    payloadAttributes,
                    forkChoiceUpdatedResult));
  }

  @Override
  public SafeFuture<ExecutionPayload> engineGetPayload(final Bytes8 payloadId, final UInt64 slot) {
    LOG.trace("calling getPayload(payloadId={}, slot={})", payloadId, slot);

    return executionEngineClient
        .getPayload(payloadId)
        .thenApply(ExecutionLayerChannelImpl::unwrapResponseOrThrow)
        .thenCombine(
            SafeFuture.of(
                () ->
                    SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions())
                        .getExecutionPayloadSchema()),
            ExecutionPayloadV1::asInternalExecutionPayload)
        .thenPeek(
            executionPayload ->
                LOG.trace(
                    "getPayload(payloadId={}, slot={}) -> {}", payloadId, slot, executionPayload));
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload) {
    LOG.trace("calling newPayload(executionPayload={})", executionPayload);

    return executionEngineClient
        .newPayload(ExecutionPayloadV1.fromInternalExecutionPayload(executionPayload))
        .thenApply(ExecutionLayerChannelImpl::unwrapResponseOrThrow)
        .thenApply(PayloadStatusV1::asInternalExecutionPayload)
        .thenPeek(
            payloadStatus ->
                LOG.trace("newPayload(executionPayload={}) -> {}", executionPayload, payloadStatus))
        .exceptionally(PayloadStatus::failedExecution);
  }

  @Override
  public SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
      TransitionConfiguration transitionConfiguration) {
    LOG.trace(
        "calling exchangeTransitionConfiguration(transitionConfiguration={})",
        transitionConfiguration);

    return executionEngineClient
        .exchangeTransitionConfiguration(
            TransitionConfigurationV1.fromInternalTransitionConfiguration(transitionConfiguration))
        .thenApply(ExecutionLayerChannelImpl::unwrapResponseOrThrow)
        .thenApply(TransitionConfigurationV1::asInternalTransitionConfiguration)
        .thenPeek(
            remoteTransitionConfiguration ->
                LOG.trace(
                    "exchangeTransitionConfiguration(transitionConfiguration={}) -> {}",
                    transitionConfiguration,
                    remoteTransitionConfiguration));
  }

  @Override
  public SafeFuture<ExecutionPayloadHeader> getPayloadHeader(
      final Bytes8 payloadId, final UInt64 slot) {
    LOG.trace("calling getPayloadHeader(payloadId={}, slot={})", payloadId, slot);

    return executionEngineClient
        .getPayloadHeader(payloadId)
        .thenApply(ExecutionLayerChannelImpl::unwrapResponseOrThrow)
        .thenCombine(
            SafeFuture.of(
                () ->
                    SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions())
                        .getExecutionPayloadHeaderSchema()),
            ExecutionPayloadHeaderV1::asInternalExecutionPayloadHeader)
        .thenPeek(
            executionPayloadHeader ->
                LOG.trace(
                    "getPayloadHeader(payloadId={}, slot={}) -> {}",
                    payloadId,
                    slot,
                    executionPayloadHeader));
  }

  @Override
  public SafeFuture<ExecutionPayload> proposeBlindedBlock(
      SignedBeaconBlock signedBlindedBeaconBlock) {
    LOG.trace("calling proposeBlindedBlock(signedBlindedBeaconBlock={})", signedBlindedBeaconBlock);

    checkArgument(
        signedBlindedBeaconBlock.getMessage().getBody().isBlinded(),
        "SignedBeaconBlock must be blind");

    return executionEngineClient
        .proposeBlindedBlock(signedBlindedBeaconBlock)
        .thenApply(ExecutionLayerChannelImpl::unwrapResponseOrThrow)
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
                    "proposeBlindedBlock(signedBlindedBeaconBlock={}) -> {}",
                    signedBlindedBeaconBlock,
                    executionPayload));
  }
}
