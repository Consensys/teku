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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteListImpl;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;

public class ExecutionPayloadV1 extends ExecutionPayloadCommon {
  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = BytesDeserializer.class)
  public final List<Bytes> transactions;

  public ExecutionPayloadV1(
      @JsonProperty("parentHash") Bytes32 parentHash,
      @JsonProperty("feeRecipient") Bytes20 feeRecipient,
      @JsonProperty("stateRoot") Bytes32 stateRoot,
      @JsonProperty("receiptsRoot") Bytes32 receiptsRoot,
      @JsonProperty("logsBloom") Bytes logsBloom,
      @JsonProperty("prevRandao") Bytes32 prevRandao,
      @JsonProperty("blockNumber") UInt64 blockNumber,
      @JsonProperty("gasLimit") UInt64 gasLimit,
      @JsonProperty("gasUsed") UInt64 gasUsed,
      @JsonProperty("timestamp") UInt64 timestamp,
      @JsonProperty("extraData") Bytes extraData,
      @JsonProperty("baseFeePerGas") UInt256 baseFeePerGas,
      @JsonProperty("blockHash") Bytes32 blockHash,
      @JsonProperty("transactions") List<Bytes> transactions) {
    super(
        parentHash,
        feeRecipient,
        stateRoot,
        receiptsRoot,
        logsBloom,
        prevRandao,
        blockNumber,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFeePerGas,
        blockHash);
    this.transactions = transactions != null ? transactions : List.of();
  }

  public ExecutionPayload asInternalExecutionPayload(
      ExecutionPayloadSchema<?> executionPayloadSchema) {
    return executionPayloadSchema.createExecutionPayload(
        builder -> applyToBuilder(executionPayloadSchema, builder));
  }

  protected ExecutionPayloadBuilder applyToBuilder(
      final ExecutionPayloadSchema<?> executionPayloadSchema,
      final ExecutionPayloadBuilder builder) {
    return builder
        .parentHash(parentHash)
        .feeRecipient(feeRecipient)
        .stateRoot(stateRoot)
        .receiptsRoot(receiptsRoot)
        .logsBloom(logsBloom)
        .prevRandao(prevRandao)
        .blockNumber(blockNumber)
        .gasLimit(gasLimit)
        .gasUsed(gasUsed)
        .timestamp(timestamp)
        .extraData(extraData)
        .baseFeePerGas(baseFeePerGas)
        .blockHash(blockHash)
        .transactions(transactions);
  }

  public static ExecutionPayloadV1 fromInternalExecutionPayload(ExecutionPayload executionPayload) {
    return new ExecutionPayloadV1(
        executionPayload.getParentHash(),
        executionPayload.getFeeRecipient(),
        executionPayload.getStateRoot(),
        executionPayload.getReceiptsRoot(),
        executionPayload.getLogsBloom(),
        executionPayload.getPrevRandao(),
        executionPayload.getBlockNumber(),
        executionPayload.getGasLimit(),
        executionPayload.getGasUsed(),
        executionPayload.getTimestamp(),
        executionPayload.getExtraData(),
        executionPayload.getBaseFeePerGas(),
        executionPayload.getBlockHash(),
        executionPayload.getTransactions().stream()
            .map(SszByteListImpl::getBytes)
            .collect(Collectors.toList()));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ExecutionPayloadV1 that = (ExecutionPayloadV1) o;
    return Objects.equals(transactions, that.transactions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), transactions);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("parentHash", parentHash)
        .add("feeRecipient", feeRecipient)
        .add("stateRoot", stateRoot)
        .add("receiptsRoot", receiptsRoot)
        .add("logsBloom", logsBloom)
        .add("prevRandao", prevRandao)
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
