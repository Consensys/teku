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

package tech.pegasys.teku.api.schema.merge;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES20;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_BYTES20;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_BYTES32;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public abstract class ExecutionPayloadCommon {

  @JsonProperty("parent_hash")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public final Bytes32 parentHash;

  @JsonProperty("fee_recipient")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES20,
      description = DESCRIPTION_BYTES20)
  public final Bytes20 feeRecipient;

  @JsonProperty("state_root")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public final Bytes32 stateRoot;

  @JsonProperty("receipt_root")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public final Bytes32 receiptRoot;

  @JsonProperty("logs_bloom")
  @Schema(type = "string", format = "byte")
  public final Bytes logsBloom;

  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public final Bytes32 random;

  @JsonProperty("block_number")
  @Schema(type = "string", format = "uint64")
  public final UInt64 blockNumber;

  @JsonProperty("gas_limit")
  @Schema(type = "string", format = "uint64")
  public final UInt64 gasLimit;

  @JsonProperty("gas_used")
  @Schema(type = "string", format = "uint64")
  public final UInt64 gasUsed;

  @Schema(type = "string", format = "uint64")
  public final UInt64 timestamp;

  @JsonProperty("extra_data")
  @Schema(type = "string", format = "byte")
  public final Bytes extraData;

  @JsonProperty("base_fee_per_gas")
  @Schema(type = "string", format = "uint256")
  public final UInt256 baseFeePerGas;

  @JsonProperty("block_hash")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public final Bytes32 blockHash;

  public ExecutionPayloadCommon(
      @JsonProperty("parent_hash") Bytes32 parentHash,
      @JsonProperty("fee_recipient") Bytes20 feeRecipient,
      @JsonProperty("state_root") Bytes32 stateRoot,
      @JsonProperty("receipt_root") Bytes32 receiptRoot,
      @JsonProperty("logs_bloom") Bytes logsBloom,
      @JsonProperty("random") Bytes32 random,
      @JsonProperty("block_number") UInt64 blockNumber,
      @JsonProperty("gas_limit") UInt64 gasLimit,
      @JsonProperty("gas_used") UInt64 gasUsed,
      @JsonProperty("timestamp") UInt64 timestamp,
      @JsonProperty("extra_data") Bytes extraData,
      @JsonProperty("base_fee_per_gas") UInt256 baseFeePerGas,
      @JsonProperty("block_hash") Bytes32 blockHash) {
    this.parentHash = parentHash;
    this.feeRecipient = feeRecipient;
    this.stateRoot = stateRoot;
    this.receiptRoot = receiptRoot;
    this.logsBloom = logsBloom;
    this.random = random;
    this.blockNumber = blockNumber;
    this.gasLimit = gasLimit;
    this.gasUsed = gasUsed;
    this.timestamp = timestamp;
    this.extraData = extraData;
    this.baseFeePerGas = baseFeePerGas;
    this.blockHash = blockHash;
  }

  public ExecutionPayloadCommon(
      tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload executionPayload) {
    this.parentHash = executionPayload.getParentHash();
    this.feeRecipient = executionPayload.getFeeRecipient();
    this.stateRoot = executionPayload.getStateRoot();
    this.receiptRoot = executionPayload.getReceiptRoot();
    this.logsBloom = executionPayload.getLogsBloom();
    this.random = executionPayload.getRandom();
    this.blockNumber = executionPayload.getBlockNumber();
    this.gasLimit = executionPayload.getGasLimit();
    this.gasUsed = executionPayload.getGasUsed();
    this.timestamp = executionPayload.getTimestamp();
    this.extraData = executionPayload.getExtraData();
    this.baseFeePerGas = executionPayload.getBaseFeePerGas();
    this.blockHash = executionPayload.getBlockHash();
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
        && Objects.equals(feeRecipient, that.feeRecipient)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(receiptRoot, that.receiptRoot)
        && Objects.equals(logsBloom, that.logsBloom)
        && Objects.equals(random, that.random)
        && Objects.equals(blockNumber, that.blockNumber)
        && Objects.equals(gasLimit, that.gasLimit)
        && Objects.equals(gasUsed, that.gasUsed)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(extraData, that.extraData)
        && Objects.equals(baseFeePerGas, that.baseFeePerGas)
        && Objects.equals(blockHash, that.blockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        parentHash,
        feeRecipient,
        stateRoot,
        receiptRoot,
        logsBloom,
        random,
        blockNumber,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFeePerGas,
        blockHash);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("parentHash", parentHash)
        .add("feeRecipient", feeRecipient)
        .add("stateRoot", stateRoot)
        .add("receiptRoot", receiptRoot)
        .add("logsBloom", logsBloom)
        .add("random", random)
        .add("blockNumber", blockNumber)
        .add("gasLimit", gasLimit)
        .add("gasUsed", gasUsed)
        .add("timestamp", timestamp)
        .add("extraData", extraData)
        .add("baseFeePerGas", baseFeePerGas)
        .add("blockHash", blockHash)
        .toString();
  }
}
