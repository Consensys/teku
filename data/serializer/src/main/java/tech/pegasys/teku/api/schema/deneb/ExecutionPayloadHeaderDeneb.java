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

package tech.pegasys.teku.api.schema.deneb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.api.schema.capella.ExecutionPayloadHeaderCapella;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;

public class ExecutionPayloadHeaderDeneb extends ExecutionPayloadHeaderCapella {

  @JsonProperty("data_gas_used")
  public final UInt64 dataGasUsed;

  @JsonProperty("excess_data_gas")
  public final UInt64 excessDataGas;

  @JsonCreator
  public ExecutionPayloadHeaderDeneb(
      @JsonProperty("parent_hash") final Bytes32 parentHash,
      @JsonProperty("fee_recipient") final Bytes20 feeRecipient,
      @JsonProperty("state_root") final Bytes32 stateRoot,
      @JsonProperty("receipts_root") final Bytes32 receiptsRoot,
      @JsonProperty("logs_bloom") final Bytes logsBloom,
      @JsonProperty("prev_randao") final Bytes32 prevRandao,
      @JsonProperty("block_number") final UInt64 blockNumber,
      @JsonProperty("gas_limit") final UInt64 gasLimit,
      @JsonProperty("gas_used") final UInt64 gasUsed,
      @JsonProperty("timestamp") final UInt64 timestamp,
      @JsonProperty("extra_data") final Bytes extraData,
      @JsonProperty("base_fee_per_gas") final UInt256 baseFeePerGas,
      @JsonProperty("block_hash") final Bytes32 blockHash,
      @JsonProperty("transactions_root") final Bytes32 transactionsRoot,
      @JsonProperty("withdrawals_root") final Bytes32 withdrawalsRoot,
      @JsonProperty("data_gas_used") final UInt64 dataGasUsed,
      @JsonProperty("excess_data_gas") final UInt64 excessDataGas) {
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
        blockHash,
        transactionsRoot,
        withdrawalsRoot);
    this.dataGasUsed = dataGasUsed;
    this.excessDataGas = excessDataGas;
  }

  public ExecutionPayloadHeaderDeneb(final ExecutionPayloadHeader executionPayloadHeader) {
    super(
        executionPayloadHeader.getParentHash(),
        executionPayloadHeader.getFeeRecipient(),
        executionPayloadHeader.getStateRoot(),
        executionPayloadHeader.getReceiptsRoot(),
        executionPayloadHeader.getLogsBloom(),
        executionPayloadHeader.getPrevRandao(),
        executionPayloadHeader.getBlockNumber(),
        executionPayloadHeader.getGasLimit(),
        executionPayloadHeader.getGasUsed(),
        executionPayloadHeader.getTimestamp(),
        executionPayloadHeader.getExtraData(),
        executionPayloadHeader.getBaseFeePerGas(),
        executionPayloadHeader.getBlockHash(),
        executionPayloadHeader.getTransactionsRoot(),
        executionPayloadHeader.getOptionalWithdrawalsRoot().orElseThrow());
    this.dataGasUsed = executionPayloadHeader.toVersionDeneb().orElseThrow().getDataGasUsed();
    this.excessDataGas = executionPayloadHeader.toVersionDeneb().orElseThrow().getExcessDataGas();
  }

  @Override
  public ExecutionPayloadHeader asInternalExecutionPayloadHeader(
      final ExecutionPayloadHeaderSchema<?> schema) {
    return schema.createExecutionPayloadHeader(
        payloadBuilder ->
            payloadBuilder
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
                .transactionsRoot(transactionsRoot)
                .withdrawalsRoot(() -> withdrawalsRoot)
                .dataGasUsed(() -> dataGasUsed)
                .excessDataGas(() -> excessDataGas));
  }

  @Override
  public Optional<ExecutionPayloadHeaderDeneb> toVersionDeneb() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ExecutionPayloadHeaderDeneb)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ExecutionPayloadHeaderDeneb that = (ExecutionPayloadHeaderDeneb) o;
    return Objects.equals(dataGasUsed, that.dataGasUsed)
        && Objects.equals(excessDataGas, that.excessDataGas);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), dataGasUsed, excessDataGas);
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
        .add("transactionsRoot", transactionsRoot)
        .add("withdrawalsRoot", withdrawalsRoot)
        .add("dataGasUsed", dataGasUsed)
        .add("excessDataGas", excessDataGas)
        .toString();
  }
}
