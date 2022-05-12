/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static tech.pegasys.teku.spec.config.Constants.EL_BUILDER_STATUS_TIMEOUT;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.ethereum.executionclient.BuilderApiMethod;
import tech.pegasys.teku.ethereum.executionclient.ExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.schema.BlindedBeaconBlockV1;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderBidV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.SignedMessage;
import tech.pegasys.teku.ethereum.executionclient.schema.ValidatorRegistrationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RestExecutionBuilderClient implements ExecutionBuilderClient {

  private final RestClient restClient;

  public RestExecutionBuilderClient(final RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public SafeFuture<Response<Void>> status() {
    return restClient
        .getAsync(BuilderApiMethod.GET_STATUS.getPath())
        .orTimeout(EL_BUILDER_STATUS_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<Void>> registerValidator(
      final SignedMessage<ValidatorRegistrationV1> signedValidatorRegistrationV1) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SafeFuture<Response<SignedMessage<BuilderBidV1>>> getHeader(
      final UInt64 slot, final Bytes48 pubKey, final Bytes32 parentHash) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayload(
      final SignedMessage<BlindedBeaconBlockV1> signedBlindedBeaconBlock) {
    throw new UnsupportedOperationException();
  }
}
