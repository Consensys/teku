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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.web3j.protocol.core.Request;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes20Serializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.Bytes8Deserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt256AsHexSerializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
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
                KintsugiExecutionPayloadV1.fromExecutionPayloadV1(executionPayload)),
            getWeb3JClient().getWeb3jService(),
            NewPayloadWeb3jResponse.class);
    return getWeb3JClient().doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayload(Bytes8 payloadId) {
    Request<?, KilnV1GetPayloadWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_getPayloadV1",
            Collections.singletonList(payloadId.toHexString()),
            getWeb3JClient().getWeb3jService(),
            KilnV1GetPayloadWeb3jResponse.class);
    return getWeb3JClient().doRequest(web3jRequest).thenApply(this::fromKilnV1GetPayloadResponse);
  }

  private Response<ExecutionPayloadV1> fromKilnV1GetPayloadResponse(
      final Response<KintsugiExecutionPayloadV1> kintsugiResponse) {
    if (kintsugiResponse.getPayload() == null) {
      return new Response<>(kintsugiResponse.getErrorMessage());
    }

    return new Response<>(kintsugiResponse.getPayload().asExecutionPayloadV1());
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
                    .map(KitsugiPayloadAttributesV1::fromPayloadAttributesV1)
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

  public static class KintsugiExecutionPayloadV1 {

    @JsonSerialize(using = BytesSerializer.class)
    @JsonDeserialize(using = Bytes32Deserializer.class)
    public final Bytes32 parentHash;

    @JsonSerialize(using = Bytes20Serializer.class)
    @JsonDeserialize(using = Bytes20Deserializer.class)
    public final Bytes20 feeRecipient;

    @JsonSerialize(using = BytesSerializer.class)
    @JsonDeserialize(using = Bytes32Deserializer.class)
    public final Bytes32 stateRoot;

    @JsonSerialize(using = BytesSerializer.class)
    @JsonDeserialize(using = Bytes32Deserializer.class)
    public final Bytes32 receiptsRoot;

    @JsonSerialize(using = BytesSerializer.class)
    @JsonDeserialize(using = BytesDeserializer.class)
    public final Bytes logsBloom;

    @JsonSerialize(using = BytesSerializer.class)
    @JsonDeserialize(using = Bytes32Deserializer.class)
    public final Bytes32 random;

    @JsonSerialize(using = UInt64AsHexSerializer.class)
    @JsonDeserialize(using = UInt64AsHexDeserializer.class)
    public final UInt64 blockNumber;

    @JsonSerialize(using = UInt64AsHexSerializer.class)
    @JsonDeserialize(using = UInt64AsHexDeserializer.class)
    public final UInt64 gasLimit;

    @JsonSerialize(using = UInt64AsHexSerializer.class)
    @JsonDeserialize(using = UInt64AsHexDeserializer.class)
    public final UInt64 gasUsed;

    @JsonSerialize(using = UInt64AsHexSerializer.class)
    @JsonDeserialize(using = UInt64AsHexDeserializer.class)
    public final UInt64 timestamp;

    @JsonSerialize(using = BytesSerializer.class)
    @JsonDeserialize(using = BytesDeserializer.class)
    public final Bytes extraData;

    @JsonSerialize(using = UInt256AsHexSerializer.class)
    @JsonDeserialize(using = UInt256AsHexDeserializer.class)
    public final UInt256 baseFeePerGas;

    @JsonSerialize(using = BytesSerializer.class)
    @JsonDeserialize(using = Bytes32Deserializer.class)
    public final Bytes32 blockHash;

    @JsonSerialize(contentUsing = BytesSerializer.class)
    @JsonDeserialize(contentUsing = BytesDeserializer.class)
    public final List<Bytes> transactions;

    public KintsugiExecutionPayloadV1(
        @JsonProperty("parentHash") Bytes32 parentHash,
        @JsonProperty("feeRecipient") Bytes20 feeRecipient,
        @JsonProperty("stateRoot") Bytes32 stateRoot,
        @JsonProperty("receiptsRoot") Bytes32 receiptsRoot,
        @JsonProperty("logsBloom") Bytes logsBloom,
        @JsonProperty("random") Bytes32 random,
        @JsonProperty("blockNumber") UInt64 blockNumber,
        @JsonProperty("gasLimit") UInt64 gasLimit,
        @JsonProperty("gasUsed") UInt64 gasUsed,
        @JsonProperty("timestamp") UInt64 timestamp,
        @JsonProperty("extraData") Bytes extraData,
        @JsonProperty("baseFeePerGas") UInt256 baseFeePerGas,
        @JsonProperty("blockHash") Bytes32 blockHash,
        @JsonProperty("transactions") List<Bytes> transactions) {
      checkNotNull(parentHash, "parentHash");
      checkNotNull(feeRecipient, "feeRecipient");
      checkNotNull(stateRoot, "stateRoot");
      checkNotNull(receiptsRoot, "receiptsRoot");
      checkNotNull(logsBloom, "logsBloom");
      checkNotNull(random, "random");
      checkNotNull(blockNumber, "blockNumber");
      checkNotNull(gasLimit, "gasLimit");
      checkNotNull(gasUsed, "gasUsed");
      checkNotNull(timestamp, "timestamp");
      checkNotNull(extraData, "extraData");
      checkNotNull(baseFeePerGas, "baseFeePerGas");
      checkNotNull(blockHash, "blockHash");
      this.parentHash = parentHash;
      this.feeRecipient = feeRecipient;
      this.stateRoot = stateRoot;
      this.receiptsRoot = receiptsRoot;
      this.logsBloom = logsBloom;
      this.random = random;
      this.blockNumber = blockNumber;
      this.gasLimit = gasLimit;
      this.gasUsed = gasUsed;
      this.timestamp = timestamp;
      this.extraData = extraData;
      this.baseFeePerGas = baseFeePerGas;
      this.blockHash = blockHash;
      this.transactions = transactions != null ? transactions : List.of();
    }

    public ExecutionPayloadV1 asExecutionPayloadV1() {
      return new ExecutionPayloadV1(
          parentHash,
          feeRecipient,
          stateRoot,
          receiptsRoot,
          logsBloom,
          random,
          blockNumber,
          gasLimit,
          gasUsed,
          timestamp,
          extraData,
          baseFeePerGas,
          blockHash,
          transactions);
    }

    public static KintsugiExecutionPayloadV1 fromExecutionPayloadV1(
        ExecutionPayloadV1 executionPayload) {
      return new KintsugiExecutionPayloadV1(
          executionPayload.parentHash,
          executionPayload.feeRecipient,
          executionPayload.stateRoot,
          executionPayload.receiptsRoot,
          executionPayload.logsBloom,
          executionPayload.prevRandao,
          executionPayload.blockNumber,
          executionPayload.gasLimit,
          executionPayload.gasUsed,
          executionPayload.timestamp,
          executionPayload.extraData,
          executionPayload.baseFeePerGas,
          executionPayload.blockHash,
          executionPayload.transactions);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final KintsugiExecutionPayloadV1 that = (KintsugiExecutionPayloadV1) o;
      return Objects.equals(parentHash, that.parentHash)
          && Objects.equals(feeRecipient, that.feeRecipient)
          && Objects.equals(stateRoot, that.stateRoot)
          && Objects.equals(receiptsRoot, that.receiptsRoot)
          && Objects.equals(logsBloom, that.logsBloom)
          && Objects.equals(random, that.random)
          && Objects.equals(blockNumber, that.blockNumber)
          && Objects.equals(gasLimit, that.gasLimit)
          && Objects.equals(gasUsed, that.gasUsed)
          && Objects.equals(timestamp, that.timestamp)
          && Objects.equals(extraData, that.extraData)
          && Objects.equals(baseFeePerGas, that.baseFeePerGas)
          && Objects.equals(blockHash, that.blockHash)
          && Objects.equals(transactions, that.transactions);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          parentHash,
          feeRecipient,
          stateRoot,
          receiptsRoot,
          logsBloom,
          random,
          blockNumber,
          gasLimit,
          gasUsed,
          timestamp,
          extraData,
          baseFeePerGas,
          blockHash,
          transactions);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("parentHash", parentHash)
          .add("feeRecipient", feeRecipient)
          .add("stateRoot", stateRoot)
          .add("receiptsRoot", receiptsRoot)
          .add("logsBloom", logsBloom)
          .add("random", random)
          .add("blockNumber", blockNumber)
          .add("gasLimit", gasLimit)
          .add("gasUsed", gasUsed)
          .add("timestamp", timestamp)
          .add("extraData", extraData)
          .add("baseFeePerGas", baseFeePerGas)
          .add("blockHash", blockHash)
          .add("transactions", transactions)
          .toString();
    }
  }

  public static class KitsugiPayloadAttributesV1 {
    @JsonSerialize(using = UInt64AsHexSerializer.class)
    @JsonDeserialize(using = UInt64AsHexDeserializer.class)
    private final UInt64 timestamp;

    @JsonSerialize(using = BytesSerializer.class)
    @JsonDeserialize(using = Bytes32Deserializer.class)
    private final Bytes32 random;

    @JsonSerialize(using = Bytes20Serializer.class)
    @JsonDeserialize(using = Bytes20Deserializer.class)
    private final Bytes20 suggestedFeeRecipient;

    public KitsugiPayloadAttributesV1(
        @JsonProperty("timestamp") UInt64 timestamp,
        @JsonProperty("random") Bytes32 random,
        @JsonProperty("suggestedFeeRecipient") Bytes20 suggestedFeeRecipient) {
      checkNotNull(timestamp, "timestamp");
      checkNotNull(random, "random");
      checkNotNull(suggestedFeeRecipient, "suggestedFeeRecipient");
      this.timestamp = timestamp;
      this.random = random;
      this.suggestedFeeRecipient = suggestedFeeRecipient;
    }

    public static KitsugiPayloadAttributesV1 fromPayloadAttributesV1(
        PayloadAttributesV1 payloadAttributes) {
      return new KitsugiPayloadAttributesV1(
          payloadAttributes.timestamp,
          payloadAttributes.prevRandao,
          payloadAttributes.suggestedFeeRecipient);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final KitsugiPayloadAttributesV1 that = (KitsugiPayloadAttributesV1) o;
      return Objects.equals(timestamp, that.timestamp)
          && Objects.equals(random, that.random)
          && Objects.equals(suggestedFeeRecipient, that.suggestedFeeRecipient);
    }

    @Override
    public int hashCode() {
      return Objects.hash(timestamp, random, suggestedFeeRecipient);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("timestamp", timestamp)
          .add("random", random)
          .add("suggestedFeeRecipient", suggestedFeeRecipient)
          .toString();
    }
  }

  static class KilnV1GetPayloadWeb3jResponse
      extends org.web3j.protocol.core.Response<KintsugiExecutionPayloadV1> {}

  static class KintsugiForkChoiceUpdatedWeb3jResponse
      extends org.web3j.protocol.core.Response<KintsugiForkChoiceUpdatedResult> {}
}
