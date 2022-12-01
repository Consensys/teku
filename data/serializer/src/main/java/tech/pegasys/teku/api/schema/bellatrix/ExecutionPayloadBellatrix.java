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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.api.schema.ExecutionPayload;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class ExecutionPayloadBellatrix extends ExecutionPayloadCommon implements ExecutionPayload {

  @ArraySchema(schema = @Schema(type = "string", format = "byte"))
  public final List<Bytes> transactions;

  @JsonCreator
  public ExecutionPayloadBellatrix(
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
      @JsonProperty("block_hash") Bytes32 blockHash,
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
    this.transactions = transactions != null ? transactions : Collections.emptyList();
  }

  public ExecutionPayloadBellatrix(
      tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload executionPayload) {
    super(
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
        executionPayload.getBlockHash());
    this.transactions =
        executionPayload.getTransactions().stream()
            .map(Transaction::getBytes)
            .collect(Collectors.toList());
  }

  public tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload
      asInternalExecutionPayload(final Spec spec, final UInt64 slot) {
    return asInternalExecutionPayload(spec.atSlot(slot));
  }

  public tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload
      asInternalExecutionPayload(final SpecVersion spec) {
    final Optional<SchemaDefinitionsBellatrix> maybeSchema =
        spec.getSchemaDefinitions().toVersionBellatrix();

    if (maybeSchema.isEmpty()) {
      throw new IllegalArgumentException(
          "Could not create execution payload at pre-bellatrix slot");
    }

    final ExecutionPayloadSchema<?> executionPayloadSchema =
        maybeSchema.get().getExecutionPayloadSchema();
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

  @Override
  public Optional<ExecutionPayloadBellatrix> toVersionBellatrix() {
    return Optional.of(this);
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
    final ExecutionPayloadBellatrix that = (ExecutionPayloadBellatrix) o;
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
