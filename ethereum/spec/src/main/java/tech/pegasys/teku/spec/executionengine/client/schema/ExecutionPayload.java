/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.spec.executionengine.client.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionengine.client.serializer.Bytes20Deserializer;
import tech.pegasys.teku.spec.executionengine.client.serializer.Bytes20Serializer;
import tech.pegasys.teku.spec.executionengine.client.serializer.Bytes32Deserializer;
import tech.pegasys.teku.spec.executionengine.client.serializer.BytesDeserializer;
import tech.pegasys.teku.spec.executionengine.client.serializer.BytesSerializer;
import tech.pegasys.teku.spec.executionengine.client.serializer.UInt64AsHexDeserializer;
import tech.pegasys.teku.spec.executionengine.client.serializer.UInt64AsHexSerializer;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.type.Bytes20;

public class ExecutionPayload {

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 blockHash;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 parentHash;

  @JsonSerialize(using = Bytes20Serializer.class)
  @JsonDeserialize(using = Bytes20Deserializer.class)
  public final Bytes20 miner;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 stateRoot;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 number;

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
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 receiptsRoot;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = BytesDeserializer.class)
  public final Bytes logsBloom;

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = BytesDeserializer.class)
  public final List<Bytes> transactions;

  public ExecutionPayload(
      @JsonProperty("blockHash") Bytes32 blockHash,
      @JsonProperty("parentHash") Bytes32 parentHash,
      @JsonProperty("miner") Bytes20 miner,
      @JsonProperty("stateRoot") Bytes32 stateRoot,
      @JsonProperty("number") UInt64 number,
      @JsonProperty("gasLimit") UInt64 gasLimit,
      @JsonProperty("gasUsed") UInt64 gasUsed,
      @JsonProperty("timestamp") UInt64 timestamp,
      @JsonProperty("receiptRoot") Bytes32 receiptsRoot,
      @JsonProperty("logsBloom") Bytes logsBloom,
      @JsonProperty("transactions") List<Bytes> transactions) {
    this.blockHash = blockHash;
    this.parentHash = parentHash;
    this.miner = miner;
    this.stateRoot = stateRoot;
    this.number = number;
    this.gasLimit = gasLimit;
    this.gasUsed = gasUsed;
    this.timestamp = timestamp;
    this.receiptsRoot = receiptsRoot;
    this.logsBloom = logsBloom;
    this.transactions = transactions != null ? transactions : Collections.emptyList();
  }

  public ExecutionPayload(
      tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload executionPayload) {
    this.blockHash = executionPayload.getBlock_hash();
    this.parentHash = executionPayload.getParent_hash();
    this.miner = executionPayload.getCoinbase();
    this.stateRoot = executionPayload.getState_root();
    this.number = executionPayload.getNumber();
    this.gasLimit = executionPayload.getGas_limit();
    this.gasUsed = executionPayload.getGas_used();
    this.timestamp = executionPayload.getTimestamp();
    this.receiptsRoot = executionPayload.getReceipt_root();
    this.logsBloom = executionPayload.getLogs_bloom();
    this.transactions =
        executionPayload.getTransactions().stream()
            .map(SszByteList::getBytes)
            .collect(Collectors.toList());
  }

  public tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload
      asInternalExecutionPayload() {
    return new tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload(
        blockHash,
        parentHash,
        miner,
        stateRoot,
        number,
        gasLimit,
        gasUsed,
        timestamp,
        receiptsRoot,
        logsBloom,
        transactions);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExecutionPayload that = (ExecutionPayload) o;
    return Objects.equals(blockHash, that.blockHash)
        && Objects.equals(parentHash, that.parentHash)
        && Objects.equals(miner, that.miner)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(number, that.number)
        && Objects.equals(gasLimit, that.gasLimit)
        && Objects.equals(gasUsed, that.gasUsed)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(receiptsRoot, that.receiptsRoot)
        && Objects.equals(logsBloom, that.logsBloom)
        && Objects.equals(transactions, that.transactions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        blockHash,
        parentHash,
        miner,
        stateRoot,
        number,
        gasLimit,
        gasUsed,
        timestamp,
        receiptsRoot,
        logsBloom,
        transactions);
  }

  @Override
  public String toString() {
    return "ExecutionPayload{"
        + "blockHash="
        + LogFormatter.formatHashRoot(blockHash)
        + ", parentHash="
        + LogFormatter.formatHashRoot(parentHash)
        + ", miner="
        + miner
        + ", stateRoot="
        + LogFormatter.formatHashRoot(stateRoot)
        + ", number="
        + number
        + ", gasLimit="
        + gasLimit
        + ", gasUsed="
        + gasUsed
        + ", timestamp="
        + timestamp
        + ", receiptsRoot="
        + LogFormatter.formatHashRoot(receiptsRoot)
        + ", logsBloom="
        + logsBloom.toHexString().substring(0, 8)
        + ", transactions=["
        + transactions.stream()
            .map(tx -> tx.toHexString().substring(0, 8))
            .collect(Collectors.joining(", "))
        + "]}";
  }
}
