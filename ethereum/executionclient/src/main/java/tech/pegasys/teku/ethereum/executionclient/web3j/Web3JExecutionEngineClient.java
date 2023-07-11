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

package tech.pegasys.teku.ethereum.executionclient.web3j;

import static tech.pegasys.teku.spec.config.Constants.EL_ENGINE_BLOCK_EXECUTION_TIMEOUT;
import static tech.pegasys.teku.spec.config.Constants.EL_ENGINE_NON_BLOCK_EXECUTION_TIMEOUT;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV2Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV2;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public class Web3JExecutionEngineClient implements ExecutionEngineClient {

  private static final Duration EXCHANGE_CAPABILITIES_TIMEOUT = Duration.ofSeconds(8);

  private final Web3JClient web3JClient;

  public Web3JExecutionEngineClient(final Web3JClient web3JClient) {
    this.web3JClient = web3JClient;
  }

  @Override
  public SafeFuture<PowBlock> getPowBlock(Bytes32 blockHash) {
    return web3JClient
        .doRequest(
            web3JClient.getEth1Web3j().ethGetBlockByHash(blockHash.toHexString(), false),
            EL_ENGINE_NON_BLOCK_EXECUTION_TIMEOUT)
        .thenApply(Response::getPayload)
        .thenApply(Web3JExecutionEngineClient::eth1BlockToPowBlock);
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return web3JClient
        .doRequest(
            web3JClient.getEth1Web3j().ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false),
            EL_ENGINE_NON_BLOCK_EXECUTION_TIMEOUT)
        .thenApply(Response::getPayload)
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
  public SafeFuture<Response<ExecutionPayloadV1>> getPayloadV1(Bytes8 payloadId) {
    Request<?, ExecutionPayloadV1Web3jResponse> web3jRequest =
        new Request<>(
            "engine_getPayloadV1",
            Collections.singletonList(payloadId.toHexString()),
            web3JClient.getWeb3jService(),
            ExecutionPayloadV1Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EL_ENGINE_NON_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<GetPayloadV2Response>> getPayloadV2(final Bytes8 payloadId) {
    Request<?, GetPayloadV2Web3jResponse> web3jRequest =
        new Request<>(
            "engine_getPayloadV2",
            Collections.singletonList(payloadId.toHexString()),
            web3JClient.getWeb3jService(),
            GetPayloadV2Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EL_ENGINE_NON_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<GetPayloadV3Response>> getPayloadV3(final Bytes8 payloadId) {
    final Request<?, GetPayloadV3Web3jResponse> web3jRequest =
        new Request<>(
            "engine_getPayloadV3",
            Collections.singletonList(payloadId.toHexString()),
            web3JClient.getWeb3jService(),
            GetPayloadV3Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EL_ENGINE_NON_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV1(ExecutionPayloadV1 executionPayload) {
    Request<?, PayloadStatusV1Web3jResponse> web3jRequest =
        new Request<>(
            "engine_newPayloadV1",
            Collections.singletonList(executionPayload),
            web3JClient.getWeb3jService(),
            PayloadStatusV1Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV2(
      final ExecutionPayloadV2 executionPayload) {
    Request<?, PayloadStatusV1Web3jResponse> web3jRequest =
        new Request<>(
            "engine_newPayloadV2",
            Collections.singletonList(executionPayload),
            web3JClient.getWeb3jService(),
            PayloadStatusV1Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV3(
      final ExecutionPayloadV3 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot) {
    final List<String> expectedBlobVersionedHashes =
        blobVersionedHashes.stream().map(VersionedHash::toHexString).collect(Collectors.toList());
    final Request<?, PayloadStatusV1Web3jResponse> web3jRequest =
        new Request<>(
            "engine_newPayloadV3",
            list(
                executionPayload, expectedBlobVersionedHashes, parentBeaconBlockRoot.toHexString()),
            web3JClient.getWeb3jService(),
            PayloadStatusV1Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV1(
      ForkChoiceStateV1 forkChoiceState, Optional<PayloadAttributesV1> payloadAttributes) {
    Request<?, ForkChoiceUpdatedResultWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_forkchoiceUpdatedV1",
            list(forkChoiceState, payloadAttributes.orElse(null)),
            web3JClient.getWeb3jService(),
            ForkChoiceUpdatedResultWeb3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV2(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV2> payloadAttributes) {
    Request<?, ForkChoiceUpdatedResultWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_forkchoiceUpdatedV2",
            list(forkChoiceState, payloadAttributes.orElse(null)),
            web3JClient.getWeb3jService(),
            ForkChoiceUpdatedResultWeb3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV3(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV3> payloadAttributes) {
    Request<?, ForkChoiceUpdatedResultWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_forkchoiceUpdatedV3",
            list(forkChoiceState, payloadAttributes.orElse(null)),
            web3JClient.getWeb3jService(),
            ForkChoiceUpdatedResultWeb3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<List<String>>> exchangeCapabilities(final List<String> capabilities) {
    Request<?, ExchangeCapabilitiesWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_exchangeCapabilities",
            Collections.singletonList(capabilities),
            web3JClient.getWeb3jService(),
            ExchangeCapabilitiesWeb3jResponse.class);
    return web3JClient.doRequest(web3jRequest, EXCHANGE_CAPABILITIES_TIMEOUT);
  }

  static class ExecutionPayloadV1Web3jResponse
      extends org.web3j.protocol.core.Response<ExecutionPayloadV1> {}

  static class GetPayloadV2Web3jResponse
      extends org.web3j.protocol.core.Response<GetPayloadV2Response> {}

  static class GetPayloadV3Web3jResponse
      extends org.web3j.protocol.core.Response<GetPayloadV3Response> {}

  static class PayloadStatusV1Web3jResponse
      extends org.web3j.protocol.core.Response<PayloadStatusV1> {}

  static class ForkChoiceUpdatedResultWeb3jResponse
      extends org.web3j.protocol.core.Response<ForkChoiceUpdatedResult> {}

  static class ExchangeCapabilitiesWeb3jResponse
      extends org.web3j.protocol.core.Response<List<String>> {}

  /**
   * Returns a list that supports null items.
   *
   * @param items the items to put in a list
   * @return the list
   */
  protected List<Object> list(final Object... items) {
    final List<Object> list = new ArrayList<>();
    Collections.addAll(list, items);
    return list;
  }
}
