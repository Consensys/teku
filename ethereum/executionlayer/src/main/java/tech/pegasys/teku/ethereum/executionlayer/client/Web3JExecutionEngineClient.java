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

package tech.pegasys.teku.ethereum.executionlayer.client;

import static tech.pegasys.teku.spec.config.Constants.NON_EXECUTION_TIMEOUT;
import static tech.pegasys.teku.spec.config.Constants.EXECUTION_TIMEOUT;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.web3j.protocol.ObjectMapperFactory;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadHeaderV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.TransitionConfigurationV1;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.SignedBeaconBlockSerializer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.el.Web3JClient;
import tech.pegasys.teku.infrastructure.el.schema.Response;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public class Web3JExecutionEngineClient implements ExecutionEngineClient {
  private final Web3JClient web3JClient;

  static {
    SimpleModule module = new SimpleModule("TekuEESsz", new Version(1, 0, 0, null, null, null));
    module.addSerializer(SignedBeaconBlock.class, new SignedBeaconBlockSerializer());
    ObjectMapperFactory.getObjectMapper().registerModule(module);
  }

  public Web3JExecutionEngineClient(final Web3JClient web3JClient) {
    this.web3JClient = web3JClient;
  }

  @Override
  public SafeFuture<Optional<PowBlock>> getPowBlock(Bytes32 blockHash) {
    return web3JClient
        .doWeb3JRequest(
            web3JClient
                .getEth1Web3j()
                .ethGetBlockByHash(blockHash.toHexString(), false)
                .sendAsync())
        .thenApply(EthBlock::getBlock)
        .thenApply(Web3JExecutionEngineClient::eth1BlockToPowBlock)
        .thenApply(Optional::ofNullable);
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return web3JClient
        .doWeb3JRequest(
            web3JClient
                .getEth1Web3j()
                .ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                .sendAsync())
        .thenApply(EthBlock::getBlock)
        .thenApply(Web3JExecutionEngineClient::eth1BlockToPowBlock);
  }

  private static PowBlock eth1BlockToPowBlock(EthBlock.Block eth1Block) {
    return eth1Block == null
        ? null
        : new PowBlock(
            Bytes32.fromHexStringStrict(eth1Block.getHash()),
            Bytes32.fromHexStringStrict(eth1Block.getParentHash()),
            UInt256.valueOf(eth1Block.getTotalDifficulty()),
            UInt64.valueOf(eth1Block.getTimestamp()));
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayload(Bytes8 payloadId) {
    Request<?, ExecutionPayloadV1Web3jResponse> web3jRequest =
        new Request<>(
            "engine_getPayloadV1",
            Collections.singletonList(payloadId.toHexString()),
            web3JClient.getWeb3jService(),
            ExecutionPayloadV1Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, NON_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayload(ExecutionPayloadV1 executionPayload) {
    Request<?, PayloadStatusV1Web3jResponse> web3jRequest =
        new Request<>(
            "engine_newPayloadV1",
            Collections.singletonList(executionPayload),
            web3JClient.getWeb3jService(),
            PayloadStatusV1Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdated(
      ForkChoiceStateV1 forkChoiceState, Optional<PayloadAttributesV1> payloadAttributes) {
    Request<?, ForkChoiceUpdatedResultWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_forkchoiceUpdatedV1",
            list(forkChoiceState, payloadAttributes.orElse(null)),
            web3JClient.getWeb3jService(),
            ForkChoiceUpdatedResultWeb3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<TransitionConfigurationV1>> exchangeTransitionConfiguration(
      TransitionConfigurationV1 transitionConfiguration) {
    Request<?, TransitionConfigurationV1Web3jResponse> web3jRequest =
        new Request<>(
            "engine_exchangeTransitionConfigurationV1",
            Collections.singletonList(transitionConfiguration),
            web3JClient.getWeb3jService(),
            TransitionConfigurationV1Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, NON_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadHeaderV1>> getPayloadHeader(Bytes8 payloadId) {
    Request<?, ExecutionPayloadHeaderV1Web3jResponse> web3jRequest =
        new Request<>(
            "builder_getPayloadHeaderV1",
            Collections.singletonList(payloadId.toHexString()),
            web3JClient.getWeb3jService(),
            ExecutionPayloadHeaderV1Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, NON_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> proposeBlindedBlock(
      SignedBeaconBlock signedBlindedBeaconBlock) {
    Request<?, ExecutionPayloadV1Web3jResponse> web3jRequest =
        new Request<>(
            "builder_proposeBlindedBlockV1",
            Collections.singletonList(signedBlindedBeaconBlock),
            web3JClient.getWeb3jService(),
            ExecutionPayloadV1Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, NON_EXECUTION_TIMEOUT);
  }

  protected Web3JClient getWeb3JClient() {
    return web3JClient;
  }

  static class ExecutionPayloadV1Web3jResponse
      extends org.web3j.protocol.core.Response<ExecutionPayloadV1> {}

  static class PayloadStatusV1Web3jResponse
      extends org.web3j.protocol.core.Response<PayloadStatusV1> {}

  static class ForkChoiceUpdatedResultWeb3jResponse
      extends org.web3j.protocol.core.Response<ForkChoiceUpdatedResult> {}

  static class TransitionConfigurationV1Web3jResponse
      extends org.web3j.protocol.core.Response<TransitionConfigurationV1> {}

  static class ExecutionPayloadHeaderV1Web3jResponse
      extends org.web3j.protocol.core.Response<ExecutionPayloadHeaderV1> {}

  /**
   * Returns a list that supports null items.
   *
   * @param items the items to put in a list
   * @return the list
   */
  protected List<Object> list(final Object... items) {
    final List<Object> list = new ArrayList<>();
    for (Object item : items) {
      list.add(item);
    }
    return list;
  }
}
