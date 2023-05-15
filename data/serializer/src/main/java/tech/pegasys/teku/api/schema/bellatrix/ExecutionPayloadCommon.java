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

package tech.pegasys.teku.api.schema.bellatrix;

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
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public abstract class ExecutionPayloadCommon {

  @JsonProperty("parent_hash")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public Bytes32 parentHash;

  @JsonProperty("fee_recipient")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES20,
      description = DESCRIPTION_BYTES20)
  public Bytes20 feeRecipient;

  @JsonProperty("state_root")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public Bytes32 stateRoot;

  @JsonProperty("receipts_root")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public Bytes32 receiptsRoot;

  @JsonProperty("logs_bloom")
  @Schema(type = "string", format = "byte")
  public Bytes logsBloom;

  @JsonProperty("prev_randao")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public Bytes32 prevRandao;

  @JsonProperty("block_number")
  @Schema(type = "string", format = "uint64")
  public UInt64 blockNumber;

  @JsonProperty("gas_limit")
  @Schema(type = "string", format = "uint64")
  public UInt64 gasLimit;

  @JsonProperty("gas_used")
  @Schema(type = "string", format = "uint64")
  public UInt64 gasUsed;

  @Schema(type = "string", format = "uint64")
  public UInt64 timestamp;

  @JsonProperty("extra_data")
  @Schema(type = "string", format = "byte")
  public Bytes extraData;

  @JsonProperty("base_fee_per_gas")
  @Schema(type = "string", format = "uint256")
  public UInt256 baseFeePerGas;

  @JsonProperty("block_hash")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES32,
      description = DESCRIPTION_BYTES32)
  public Bytes32 blockHash;

  public ExecutionPayloadCommon() {}

  public ExecutionPayloadCommon(
      @JsonProperty("parent_hash") Bytes32 parentHash,
      @JsonProperty("fee_recipient") Bytes20 feeRecipient,
      @JsonProperty("state_root") Bytes32 stateRoot,
      @JsonProperty("receipts_root") Bytes32 receiptsRoot,
      @JsonProperty("logs_bloom") Bytes logsBloom,
      @JsonProperty("prev_randao") Bytes32 prevRandao,
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
    this.receiptsRoot = receiptsRoot;
    this.logsBloom = logsBloom;
    this.prevRandao = prevRandao;
    this.blockNumber = blockNumber;
    this.gasLimit = gasLimit;
    this.gasUsed = gasUsed;
    this.timestamp = timestamp;
    this.extraData = extraData;
    this.baseFeePerGas = baseFeePerGas;
    this.blockHash = blockHash;
  }

  public Bytes32 getParentHash() {
    return parentHash;
  }

  public void setParentHash(Bytes32 parentHash) {
    this.parentHash = parentHash;
  }

  public Bytes20 getFeeRecipient() {
    return feeRecipient;
  }

  public void setFeeRecipient(Bytes20 feeRecipient) {
    this.feeRecipient = feeRecipient;
  }

  public Bytes32 getStateRoot() {
    return stateRoot;
  }

  public void setStateRoot(Bytes32 stateRoot) {
    this.stateRoot = stateRoot;
  }

  public Bytes32 getReceiptsRoot() {
    return receiptsRoot;
  }

  public void setReceiptsRoot(Bytes32 receiptsRoot) {
    this.receiptsRoot = receiptsRoot;
  }

  public Bytes getLogsBloom() {
    return logsBloom;
  }

  public void setLogsBloom(Bytes logsBloom) {
    this.logsBloom = logsBloom;
  }

  public Bytes32 getPrevRandao() {
    return prevRandao;
  }

  public void setPrevRandao(Bytes32 prevRandao) {
    this.prevRandao = prevRandao;
  }

  public UInt64 getBlockNumber() {
    return blockNumber;
  }

  public void setBlockNumber(UInt64 blockNumber) {
    this.blockNumber = blockNumber;
  }

  public UInt64 getGasLimit() {
    return gasLimit;
  }

  public void setGasLimit(UInt64 gasLimit) {
    this.gasLimit = gasLimit;
  }

  public UInt64 getGasUsed() {
    return gasUsed;
  }

  public void setGasUsed(UInt64 gasUsed) {
    this.gasUsed = gasUsed;
  }

  public UInt64 getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(UInt64 timestamp) {
    this.timestamp = timestamp;
  }

  public Bytes getExtraData() {
    return extraData;
  }

  public void setExtraData(Bytes extraData) {
    this.extraData = extraData;
  }

  public UInt256 getBaseFeePerGas() {
    return baseFeePerGas;
  }

  public void setBaseFeePerGas(UInt256 baseFeePerGas) {
    this.baseFeePerGas = baseFeePerGas;
  }

  public Bytes32 getBlockHash() {
    return blockHash;
  }

  public void setBlockHash(Bytes32 blockHash) {
    this.blockHash = blockHash;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExecutionPayloadCommon that = (ExecutionPayloadCommon) o;
    return Objects.equals(parentHash, that.parentHash)
        && Objects.equals(feeRecipient, that.feeRecipient)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(receiptsRoot, that.receiptsRoot)
        && Objects.equals(logsBloom, that.logsBloom)
        && Objects.equals(prevRandao, that.prevRandao)
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
        .toString();
  }
}
