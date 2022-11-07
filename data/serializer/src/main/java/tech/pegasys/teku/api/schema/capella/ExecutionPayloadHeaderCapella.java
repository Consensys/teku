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

package tech.pegasys.teku.api.schema.capella;

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
import tech.pegasys.teku.api.schema.bellatrix.ExecutionPayloadHeaderBellatrix;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ExecutionPayloadHeaderCapella extends ExecutionPayloadHeaderBellatrix {

  @JsonProperty("withdrawals_root")
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 withdrawalsRoot;

  @JsonCreator
  public ExecutionPayloadHeaderCapella(
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
      @JsonProperty("transactions_root") Bytes32 transactionsRoot,
      @JsonProperty("withdrawals_root") Bytes32 withdrawalsRoot) {
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
        transactionsRoot);
    this.withdrawalsRoot = withdrawalsRoot;
  }

  public ExecutionPayloadHeaderCapella(
      tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader
          executionPayloadHeader) {
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
        executionPayloadHeader.getTransactionsRoot());
    this.withdrawalsRoot = executionPayloadHeader.getOptionalWithdrawalsRoot().orElseThrow();
  }

  @Override
  public Optional<ExecutionPayloadHeaderCapella> toVersionCapella() {
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
    final ExecutionPayloadHeaderCapella that = (ExecutionPayloadHeaderCapella) o;
    return Objects.equals(withdrawalsRoot, that.withdrawalsRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), withdrawalsRoot);
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
        .toString();
  }
}
