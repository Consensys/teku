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

package tech.pegasys.teku.ethereum.executionclient;

import static tech.pegasys.teku.spec.config.Constants.NON_EXECUTION_TIMEOUT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.web3j.protocol.core.Request;
import tech.pegasys.teku.ethereum.executionclient.schema.BlindedBeaconBlockV1;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderBidV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.GenericBuilderStatus;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.SignedMessage;
import tech.pegasys.teku.ethereum.executionclient.schema.ValidatorRegistrationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Web3JExecutionBuilderClient implements ExecutionBuilderClient {
  private final Web3JClient web3JClient;

  public Web3JExecutionBuilderClient(final Web3JClient web3JClient) {
    this.web3JClient = web3JClient;
  }

  @Override
  public SafeFuture<Response<GenericBuilderStatus>> status() {
    Request<?, GenericBuilderStatusWeb3jResponse> web3jRequest =
        new Request<>(
            "builder_status",
            List.of(),
            web3JClient.getWeb3jService(),
            GenericBuilderStatusWeb3jResponse.class);
    return web3JClient.doRequest(web3jRequest, NON_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<GenericBuilderStatus>> registerValidator(
      final SignedMessage<ValidatorRegistrationV1> signedValidatorRegistrationV1) {
    Request<?, GenericBuilderStatusWeb3jResponse> web3jRequest =
        new Request<>(
            "builder_registerValidatorV1",
            Collections.singletonList(signedValidatorRegistrationV1),
            web3JClient.getWeb3jService(),
            GenericBuilderStatusWeb3jResponse.class);
    return web3JClient.doRequest(web3jRequest, NON_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<SignedMessage<BuilderBidV1>>> getHeader(
      final UInt64 slot, final Bytes48 pubKey, final Bytes32 parentHash) {
    Request<?, ExecutionPayloadHeaderV1Web3jResponse> web3jRequest =
        new Request<>(
            "builder_getHeaderV1",
            List.of(slot.toString(), pubKey.toHexString(), parentHash.toHexString()),
            web3JClient.getWeb3jService(),
            ExecutionPayloadHeaderV1Web3jResponse.class);
    return web3JClient.doRequest(web3jRequest, NON_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayload(
      SignedMessage<BlindedBeaconBlockV1> signedBlindedBeaconBlock) {
    Request<?, ExecutionPayloadV1Web3jResponse> web3jRequest =
        new Request<>(
            "builder_getPayloadV1",
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

  static class ExecutionPayloadHeaderV1Web3jResponse
      extends org.web3j.protocol.core.Response<SignedMessage<BuilderBidV1>> {}

  static class GenericBuilderStatusWeb3jResponse
      extends org.web3j.protocol.core.Response<GenericBuilderStatus> {}

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
