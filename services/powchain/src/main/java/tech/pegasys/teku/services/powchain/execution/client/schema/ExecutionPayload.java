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

package tech.pegasys.teku.services.powchain.execution.client.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.powchain.execution.client.serializer.Bytes20Deserializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.Bytes20Serializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.Bytes32Deserializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.BytesDeserializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.BytesSerializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.UInt64AsHexDeserializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.UInt64AsHexSerializer;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.type.Bytes20;

public class ExecutionPayload {

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 parentHash;

  @JsonSerialize(using = Bytes20Serializer.class)
  @JsonDeserialize(using = Bytes20Deserializer.class)
  public final Bytes20 miner;

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
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 baseFeePerGas;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 blockHash;

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = BytesDeserializer.class)
  public final List<Bytes> transactions;

  public ExecutionPayload(
      tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload executionPayload) {
    this.parentHash = executionPayload.getParent_hash();
    this.miner = executionPayload.getCoinbase();
    this.stateRoot = executionPayload.getState_root();
    this.receiptsRoot = executionPayload.getReceipt_root();
    this.logsBloom = executionPayload.getLogs_bloom();
    this.random = executionPayload.getRandom();
    this.blockNumber = executionPayload.getBlockNumber();
    this.gasLimit = executionPayload.getGas_limit();
    this.gasUsed = executionPayload.getGas_used();
    this.timestamp = executionPayload.getTimestamp();
    this.baseFeePerGas = executionPayload.getBaseFeePerGas();
    this.blockHash = executionPayload.getBlock_hash();
    this.transactions =
        executionPayload.getTransactions().stream()
            .map(Transaction::getOpaqueTransaction)
            .map(SszByteList::getBytes)
            .collect(Collectors.toList());
  }

  public ExecutionPayload(
      @JsonProperty("parentHash") Bytes32 parentHash,
      @JsonProperty("miner") Bytes20 miner,
      @JsonProperty("stateRoot") Bytes32 stateRoot,
      @JsonProperty("receiptRoot") Bytes32 receiptsRoot,
      @JsonProperty("logsBloom") Bytes logsBloom,
      @JsonProperty("random") Bytes32 random,
      @JsonProperty("blockNumber") UInt64 blockNumber,
      @JsonProperty("gasLimit") UInt64 gasLimit,
      @JsonProperty("gasUsed") UInt64 gasUsed,
      @JsonProperty("timestamp") UInt64 timestamp,
      @JsonProperty("blockHash") Bytes32 baseFeePerGas,
      @JsonProperty("blockHash") Bytes32 blockHash,
      @JsonProperty("transactions") List<Bytes> transactions) {
    this.parentHash = parentHash;
    this.miner = miner;
    this.stateRoot = stateRoot;
    this.receiptsRoot = receiptsRoot;
    this.logsBloom = logsBloom;
    this.random = random;
    this.blockNumber = blockNumber;
    this.gasLimit = gasLimit;
    this.gasUsed = gasUsed;
    this.timestamp = timestamp;
    this.baseFeePerGas = baseFeePerGas;
    this.blockHash = blockHash;
    this.transactions = transactions != null ? transactions : Collections.emptyList();
  }

  public tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload
      asInternalExecutionPayload() {
    return new tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload(
        parentHash,
        miner,
        stateRoot,
        receiptsRoot,
        logsBloom,
        random,
        blockNumber,
        gasLimit,
        gasUsed,
        timestamp,
        baseFeePerGas,
        blockHash,
        transactions);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExecutionPayload that = (ExecutionPayload) o;
    return Objects.equals(parentHash, that.parentHash)
        && Objects.equals(miner, that.miner)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(receiptsRoot, that.receiptsRoot)
        && Objects.equals(logsBloom, that.logsBloom)
        && Objects.equals(random, that.random)
        && Objects.equals(blockNumber, that.blockNumber)
        && Objects.equals(gasLimit, that.gasLimit)
        && Objects.equals(gasUsed, that.gasUsed)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(baseFeePerGas, that.baseFeePerGas)
        && Objects.equals(blockHash, that.blockHash)
        && Objects.equals(transactions, that.transactions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        parentHash,
        miner,
        stateRoot,
        receiptsRoot,
        logsBloom,
        random,
        blockNumber,
        gasLimit,
        gasUsed,
        timestamp,
        baseFeePerGas,
        blockHash,
        transactions);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("parentHash", parentHash)
        .add("miner", miner)
        .add("stateRoot", stateRoot)
        .add("receiptsRoot", receiptsRoot)
        .add("logsBloom", logsBloom)
        .add("random", random)
        .add("blockNumber", blockNumber)
        .add("gasLimit", gasLimit)
        .add("gasUsed", gasUsed)
        .add("timestamp", timestamp)
        .add("baseFeePerGas", baseFeePerGas)
        .add("blockHash", blockHash)
        .add("transactions", transactions)
        .toString();
  }
}
