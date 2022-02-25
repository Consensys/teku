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

package tech.pegasys.teku.ethereum.executionlayer.client;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.web3j.protocol.core.Request;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes8Deserializer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;

public class KintsugiWeb3JExecutionEngineClient extends KilnV1Web3JExecutionEngineClient {

  private final AtomicLong nextId = new AtomicLong(0);

  public KintsugiWeb3JExecutionEngineClient(String eeEndpoint, TimeProvider timeProvider) {
    super(eeEndpoint, timeProvider);
    getWeb3JClient()
        .addRequestAdapter(
            request -> {
              request.setId(nextId.getAndIncrement());
              return request;
            });
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayload(ExecutionPayloadV1 executionPayload) {
    Request<?, NewPayloadWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_executePayloadV1",
            Collections.singletonList(
                KilnV1ExecutionPayloadV1.fromExecutionPayloadV1(executionPayload)),
            getWeb3JClient().getWeb3jService(),
            NewPayloadWeb3jResponse.class);
    return getWeb3JClient().doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdated(
      ForkChoiceStateV1 forkChoiceState, Optional<PayloadAttributesV1> payloadAttributes) {
    Request<?, KintsugiForkChoiceUpdatedWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_forkchoiceUpdatedV1",
            list(
                forkChoiceState,
                payloadAttributes
                    .map(KilnV1PayloadAttributesV1::fromPayloadAttributesV1)
                    .orElse(null)),
            getWeb3JClient().getWeb3jService(),
            KintsugiForkChoiceUpdatedWeb3jResponse.class);
    return getWeb3JClient()
        .doRequest(web3jRequest)
        .thenApply(this::fromKintsugiForkChoiceUpdatedResultResponse);
  }

  private Response<ForkChoiceUpdatedResult> fromKintsugiForkChoiceUpdatedResultResponse(
      final Response<KintsugiForkChoiceUpdatedResult> kintsugiResponse) {

    if (kintsugiResponse.getPayload() == null) {
      return new Response<>(kintsugiResponse.getErrorMessage());
    }

    ExecutionPayloadStatus payloadStatus;

    switch (kintsugiResponse.getPayload().status) {
      case SUCCESS:
        payloadStatus = ExecutionPayloadStatus.VALID;
        break;
      case SYNCING:
        payloadStatus = ExecutionPayloadStatus.SYNCING;
        break;
      default:
        payloadStatus = ExecutionPayloadStatus.INVALID;
    }

    return new Response<>(
        new ForkChoiceUpdatedResult(
            new PayloadStatusV1(payloadStatus, null, null),
            kintsugiResponse.getPayload().payloadId));
  }

  public static class KintsugiForkChoiceUpdatedResult {
    private final ForkChoiceUpdatedStatus status;

    @JsonDeserialize(using = Bytes8Deserializer.class)
    private final Bytes8 payloadId;

    public KintsugiForkChoiceUpdatedResult(
        @JsonProperty("status") ForkChoiceUpdatedStatus status,
        @JsonProperty("payloadId") Bytes8 payloadId) {
      checkNotNull(status, "status");
      this.status = status;
      this.payloadId = payloadId;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final KintsugiForkChoiceUpdatedResult that = (KintsugiForkChoiceUpdatedResult) o;
      return Objects.equals(status, that.status) && Objects.equals(payloadId, that.payloadId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(status, payloadId);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("status", status)
          .add("payloadId", payloadId)
          .toString();
    }
  }

  public enum ForkChoiceUpdatedStatus {
    SUCCESS,
    SYNCING
  }

  static class KintsugiForkChoiceUpdatedWeb3jResponse
      extends org.web3j.protocol.core.Response<KintsugiForkChoiceUpdatedResult> {}
}
