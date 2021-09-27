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

package tech.pegasys.teku.api.schema.merge;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.type.Bytes20;

public class ExecutionPayload {

  public final Bytes32 parent_hash;
  public final Bytes20 coinbase;
  public final Bytes32 state_root;
  public final Bytes32 receipt_root;
  public final Bytes logs_bloom;
  public final Bytes32 random;
  public final UInt64 block_number;
  public final UInt64 gas_limit;
  public final UInt64 gas_used;
  public final UInt64 timestamp;
  public final Bytes extra_data;
  public final Bytes32 base_fee_per_gas;
  public final Bytes32 block_hash;
  public final List<Bytes> transactions;

  public ExecutionPayload(
      @JsonProperty("parent_hash") Bytes32 parent_hash,
      @JsonProperty("coinbase") Bytes20 coinbase,
      @JsonProperty("state_root") Bytes32 state_root,
      @JsonProperty("receipt_root") Bytes32 receipt_root,
      @JsonProperty("logs_bloom") Bytes logs_bloom,
      @JsonProperty("random") Bytes32 random,
      @JsonProperty("number") UInt64 block_number,
      @JsonProperty("gas_limit") UInt64 gas_limit,
      @JsonProperty("gas_used") UInt64 gas_used,
      @JsonProperty("timestamp") UInt64 timestamp,
      @JsonProperty("extra_data") Bytes extra_data,
      @JsonProperty("base_fee_per_gas") Bytes32 base_fee_per_gas,
      @JsonProperty("block_hash") Bytes32 block_hash,
      @JsonProperty("transactions") List<Bytes> transactions) {
    this.parent_hash = parent_hash;
    this.coinbase = coinbase;
    this.state_root = state_root;
    this.receipt_root = receipt_root;
    this.logs_bloom = logs_bloom;
    this.random = random;
    this.block_number = block_number;
    this.gas_limit = gas_limit;
    this.gas_used = gas_used;
    this.timestamp = timestamp;
    this.extra_data = extra_data;
    this.base_fee_per_gas = base_fee_per_gas;
    this.block_hash = block_hash;
    this.transactions = transactions != null ? transactions : Collections.emptyList();
  }

  public ExecutionPayload(
      tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload executionPayload) {
    this.parent_hash = executionPayload.getParent_hash();
    this.coinbase = executionPayload.getCoinbase();
    this.state_root = executionPayload.getState_root();
    this.receipt_root = executionPayload.getReceipt_root();
    this.logs_bloom = executionPayload.getLogs_bloom();
    this.random = executionPayload.getRandom();
    this.block_number = executionPayload.getBlockNumber();
    this.gas_limit = executionPayload.getGas_limit();
    this.gas_used = executionPayload.getGas_used();
    this.timestamp = executionPayload.getTimestamp();
    this.extra_data = executionPayload.getExtraData();
    this.base_fee_per_gas = executionPayload.getBaseFeePerGas();
    this.block_hash = executionPayload.getBlock_hash();
    this.transactions =
        executionPayload.getTransactions().stream()
            .map(Transaction::getOpaqueTransaction)
            .map(SszByteList::getBytes)
            .collect(Collectors.toList());
  }

  public tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload
      asInternalExecutionPayload() {
    return new tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload(
        parent_hash,
        coinbase,
        state_root,
        receipt_root,
        logs_bloom,
        random,
        block_number,
        gas_limit,
        gas_used,
        timestamp,
        extra_data,
        base_fee_per_gas,
        block_hash,
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
    return Objects.equals(parent_hash, that.parent_hash)
        && Objects.equals(coinbase, that.coinbase)
        && Objects.equals(state_root, that.state_root)
        && Objects.equals(receipt_root, that.receipt_root)
        && Objects.equals(logs_bloom, that.logs_bloom)
        && Objects.equals(random, that.random)
        && Objects.equals(block_number, that.block_number)
        && Objects.equals(gas_limit, that.gas_limit)
        && Objects.equals(gas_used, that.gas_used)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(extra_data, that.extra_data)
        && Objects.equals(base_fee_per_gas, that.base_fee_per_gas)
        && Objects.equals(block_hash, that.block_hash)
        && Objects.equals(transactions, that.transactions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        parent_hash,
        coinbase,
        state_root,
        receipt_root,
        logs_bloom,
        random,
        block_number,
        gas_limit,
        gas_used,
        timestamp,
        extra_data,
        base_fee_per_gas,
        block_hash,
        transactions);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("parent_hash", parent_hash)
        .add("coinbase", coinbase)
        .add("state_root", state_root)
        .add("receipt_root", receipt_root)
        .add("logs_bloom", logs_bloom)
        .add("random", random)
        .add("block_number", block_number)
        .add("gas_limit", gas_limit)
        .add("gas_used", gas_used)
        .add("timestamp", timestamp)
        .add("extra_data", extra_data)
        .add("base_fee_per_gas", base_fee_per_gas)
        .add("block_hash", block_hash)
        .add("transactions", transactions)
        .toString();
  }
}
