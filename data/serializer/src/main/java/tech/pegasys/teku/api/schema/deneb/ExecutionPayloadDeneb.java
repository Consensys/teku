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

import static tech.pegasys.teku.api.schema.capella.Withdrawal.WITHDRAWAL_TYPE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.api.schema.ExecutionPayload;
import tech.pegasys.teku.api.schema.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.api.schema.capella.Withdrawal;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableListTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;

public class ExecutionPayloadDeneb extends ExecutionPayloadCapella implements ExecutionPayload {

  @JsonProperty("excess_data_gas")
  public UInt256 excessDataGas;

  public static final DeserializableTypeDefinition<ExecutionPayloadDeneb>
      EXECUTION_PAYLOAD_DENEB_TYPE =
          DeserializableTypeDefinition.object(ExecutionPayloadDeneb.class)
              .initializer(ExecutionPayloadDeneb::new)
              .withField(
                  "transactions",
                  new DeserializableListTypeDefinition<>(CoreTypes.BYTES_TYPE),
                  ExecutionPayloadDeneb::getTransactions,
                  ExecutionPayloadDeneb::setTransactions)
              .withField(
                  "parent_hash",
                  CoreTypes.BYTES32_TYPE,
                  ExecutionPayloadDeneb::getParentHash,
                  ExecutionPayloadDeneb::setParentHash)
              .withField(
                  "fee_recipient",
                  CoreTypes.BYTES20_TYPE,
                  ExecutionPayloadDeneb::getFeeRecipient,
                  ExecutionPayloadDeneb::setFeeRecipient)
              .withField(
                  "state_root",
                  CoreTypes.BYTES32_TYPE,
                  ExecutionPayloadDeneb::getStateRoot,
                  ExecutionPayloadDeneb::setStateRoot)
              .withField(
                  "receipts_root",
                  CoreTypes.BYTES32_TYPE,
                  ExecutionPayloadDeneb::getReceiptsRoot,
                  ExecutionPayloadDeneb::setReceiptsRoot)
              .withField(
                  "logs_bloom",
                  CoreTypes.BYTES_TYPE,
                  ExecutionPayloadDeneb::getLogsBloom,
                  ExecutionPayloadDeneb::setLogsBloom)
              .withField(
                  "prev_randao",
                  CoreTypes.BYTES32_TYPE,
                  ExecutionPayloadDeneb::getPrevRandao,
                  ExecutionPayloadDeneb::setPrevRandao)
              .withField(
                  "block_number",
                  CoreTypes.UINT64_TYPE,
                  ExecutionPayloadDeneb::getBlockNumber,
                  ExecutionPayloadDeneb::setBlockNumber)
              .withField(
                  "gas_limit",
                  CoreTypes.UINT64_TYPE,
                  ExecutionPayloadDeneb::getGasLimit,
                  ExecutionPayloadDeneb::setGasLimit)
              .withField(
                  "gas_used",
                  CoreTypes.UINT64_TYPE,
                  ExecutionPayloadDeneb::getGasUsed,
                  ExecutionPayloadDeneb::setGasUsed)
              .withField(
                  "timestamp",
                  CoreTypes.UINT64_TYPE,
                  ExecutionPayloadCapella::getTimestamp,
                  ExecutionPayloadCapella::setTimestamp)
              .withField(
                  "extra_data",
                  CoreTypes.BYTES_TYPE,
                  ExecutionPayloadDeneb::getExtraData,
                  ExecutionPayloadDeneb::setExtraData)
              .withField(
                  "base_fee_per_gas",
                  CoreTypes.UINT256_TYPE,
                  ExecutionPayloadDeneb::getBaseFeePerGas,
                  ExecutionPayloadDeneb::setBaseFeePerGas)
              .withField(
                  "block_hash",
                  CoreTypes.BYTES32_TYPE,
                  ExecutionPayloadDeneb::getBlockHash,
                  ExecutionPayloadDeneb::setBlockHash)
              .withField(
                  "withdrawals",
                  new DeserializableListTypeDefinition<>(WITHDRAWAL_TYPE),
                  ExecutionPayloadDeneb::getWithdrawals,
                  ExecutionPayloadDeneb::setWithdrawals)
              .withField(
                  "excess_data_gas",
                  CoreTypes.UINT256_TYPE,
                  ExecutionPayloadDeneb::getExcessDataGas,
                  ExecutionPayloadDeneb::setExcessDataGas)
              .build();

  @JsonCreator
  public ExecutionPayloadDeneb(
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
      @JsonProperty("transactions") final List<Bytes> transactions,
      @JsonProperty("withdrawals") final List<Withdrawal> withdrawals,
      @JsonProperty("excess_data_gas") final UInt256 excessDataGas) {
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
        transactions,
        withdrawals);
    this.excessDataGas = excessDataGas;
  }

  public ExecutionPayloadDeneb(
      final tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload executionPayload) {
    super(executionPayload);
    this.excessDataGas = executionPayload.toVersionDeneb().orElseThrow().getExcessDataGas();
  }

  public ExecutionPayloadDeneb() {
    super();
  }

  public UInt256 getExcessDataGas() {
    return excessDataGas;
  }

  public void setExcessDataGas(UInt256 excessDataGas) {
    this.excessDataGas = excessDataGas;
  }

  @Override
  protected ExecutionPayloadBuilder applyToBuilder(
      final ExecutionPayloadSchema<?> executionPayloadSchema,
      final ExecutionPayloadBuilder builder) {
    return super.applyToBuilder(executionPayloadSchema, builder).excessDataGas(() -> excessDataGas);
  }

  @Override
  public Optional<ExecutionPayloadDeneb> toVersionDeneb() {
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
    final ExecutionPayloadDeneb that = (ExecutionPayloadDeneb) o;
    return Objects.equals(excessDataGas, that.excessDataGas);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), excessDataGas);
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
        .add("withdrawals", withdrawals)
        .add("excessDataGas", excessDataGas)
        .toString();
  }
}
