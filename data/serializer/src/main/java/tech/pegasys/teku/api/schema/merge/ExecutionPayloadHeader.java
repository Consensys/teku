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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;

public class ExecutionPayloadHeader extends ExecutionPayloadCommon {

  @JsonProperty("transactions_root")
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 transactionsRoot;

  @JsonCreator
  public ExecutionPayloadHeader(
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
      @JsonProperty("block_hash") Bytes32 blockHash,
      @JsonProperty("transactions_root") Bytes32 transactionsRoot) {
    super(
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
    this.transactionsRoot = transactionsRoot;
  }

  public ExecutionPayloadHeader(
      tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader
          executionPayloadHeader) {
    super(
        executionPayloadHeader.getParentHash(),
        executionPayloadHeader.getFeeRecipient(),
        executionPayloadHeader.getStateRoot(),
        executionPayloadHeader.getReceiptRoot(),
        executionPayloadHeader.getLogsBloom(),
        executionPayloadHeader.getRandom(),
        executionPayloadHeader.getBlockNumber(),
        executionPayloadHeader.getGasLimit(),
        executionPayloadHeader.getGasUsed(),
        executionPayloadHeader.getTimestamp(),
        executionPayloadHeader.getExtraData(),
        executionPayloadHeader.getBaseFeePerGas(),
        executionPayloadHeader.getBlockHash());
    this.transactionsRoot = executionPayloadHeader.getTransactionsRoot();
  }

  public Optional<tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader>
      asInternalExecutionPayloadHeader(final Spec spec, final UInt64 slot) {

    final Optional<SchemaDefinitionsMerge> maybeSchema =
        spec.atSlot(slot).getSchemaDefinitions().toVersionMerge();

    if (maybeSchema.isEmpty()) {
      final String message =
          String.format("Could not create execution payload at non-merge slot %s", slot);
      throw new IllegalArgumentException(message);
    }

    return maybeSchema.map(
        schema ->
            schema
                .getExecutionPayloadHeaderSchema()
                .create(
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
                    blockHash,
                    transactionsRoot));
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
    final ExecutionPayloadHeader that = (ExecutionPayloadHeader) o;
    return Objects.equals(transactionsRoot, that.transactionsRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), transactionsRoot);
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
        .add("transactionsRoot", transactionsRoot)
        .toString();
  }
}
