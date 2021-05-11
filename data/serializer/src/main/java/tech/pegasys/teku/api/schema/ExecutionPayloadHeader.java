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

package tech.pegasys.teku.api.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes20;

public class ExecutionPayloadHeader {

  public final Bytes32 block_hash;
  public final Bytes32 parent_hash;
  public final Bytes20 miner;
  public final Bytes32 state_root;
  public final UInt64 number;
  public final UInt64 gas_limit;
  public final UInt64 gas_used;
  public final UInt64 timestamp;
  public final Bytes32 receipt_root;
  public final Bytes logs_bloom;
  public final Bytes32 transactions_root;

  public ExecutionPayloadHeader(
      @JsonProperty("block_hash") Bytes32 block_hash,
      @JsonProperty("parent_hash") Bytes32 parent_hash,
      @JsonProperty("miner") Bytes20 miner,
      @JsonProperty("state_root") Bytes32 state_root,
      @JsonProperty("number") UInt64 number,
      @JsonProperty("gas_limit") UInt64 gas_limit,
      @JsonProperty("gas_used") UInt64 gas_used,
      @JsonProperty("timestamp") UInt64 timestamp,
      @JsonProperty("receipt_root") Bytes32 receipt_root,
      @JsonProperty("logs_bloom") Bytes logs_bloom,
      @JsonProperty("transactions_root") Bytes32 transactions_root) {
    this.block_hash = block_hash;
    this.parent_hash = parent_hash;
    this.miner = miner;
    this.state_root = state_root;
    this.number = number;
    this.gas_limit = gas_limit;
    this.gas_used = gas_used;
    this.timestamp = timestamp;
    this.receipt_root = receipt_root;
    this.logs_bloom = logs_bloom;
    this.transactions_root = transactions_root;
  }

  public ExecutionPayloadHeader(
      tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader
          executionPayloadHeader) {
    this.block_hash = executionPayloadHeader.getBlock_hash();
    this.parent_hash = executionPayloadHeader.getParent_hash();
    this.miner = executionPayloadHeader.getCoinbase();
    this.state_root = executionPayloadHeader.getState_root();
    this.number = executionPayloadHeader.getNumber();
    this.gas_limit = executionPayloadHeader.getGas_limit();
    this.gas_used = executionPayloadHeader.getGas_used();
    this.timestamp = executionPayloadHeader.getTimestamp();
    this.receipt_root = executionPayloadHeader.getReceipt_root();
    this.logs_bloom = executionPayloadHeader.getLogs_bloom();
    this.transactions_root = executionPayloadHeader.getTransactions_root();
  }

  public tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader
      asInternalExecutionPayloadHeader() {
    return new tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader(
        block_hash,
        parent_hash,
        miner,
        state_root,
        number,
        gas_limit,
        gas_used,
        timestamp,
        receipt_root,
        logs_bloom,
        transactions_root);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExecutionPayloadHeader that = (ExecutionPayloadHeader) o;
    return Objects.equals(block_hash, that.block_hash)
        && Objects.equals(parent_hash, that.parent_hash)
        && Objects.equals(miner, that.miner)
        && Objects.equals(state_root, that.state_root)
        && Objects.equals(number, that.number)
        && Objects.equals(gas_limit, that.gas_limit)
        && Objects.equals(gas_used, that.gas_used)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(receipt_root, that.receipt_root)
        && Objects.equals(logs_bloom, that.logs_bloom)
        && Objects.equals(transactions_root, that.transactions_root);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        block_hash,
        parent_hash,
        miner,
        state_root,
        number,
        gas_limit,
        gas_used,
        timestamp,
        receipt_root,
        logs_bloom,
        transactions_root);
  }

  @Override
  public String toString() {
    return "ExecutionPayload{"
        + "block_hash="
        + block_hash
        + ", parent_hash="
        + parent_hash
        + ", miner="
        + miner
        + ", state_root="
        + state_root
        + ", number="
        + number
        + ", gas_limit="
        + gas_limit
        + ", gas_used="
        + gas_used
        + ", timestamp="
        + timestamp
        + ", receipt_root="
        + receipt_root
        + ", logs_bloom="
        + logs_bloom
        + ", transactions_root="
        + transactions_root
        + "}";
  }
}
