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

package tech.pegasys.teku.ethereum.executionclient.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteListImpl;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;

public class ExecutionPayloadV3 extends ExecutionPayloadV2 {
  @JsonSerialize(using = UInt256AsHexSerializer.class)
  @JsonDeserialize(using = UInt256AsHexDeserializer.class)
  public final UInt256 excessDataGas;

  public ExecutionPayloadV3(
      @JsonProperty("parentHash") Bytes32 parentHash,
      @JsonProperty("feeRecipient") Bytes20 feeRecipient,
      @JsonProperty("stateRoot") Bytes32 stateRoot,
      @JsonProperty("receiptsRoot") Bytes32 receiptsRoot,
      @JsonProperty("logsBloom") Bytes logsBloom,
      @JsonProperty("prevRandao") Bytes32 prevRandao,
      @JsonProperty("blockNumber") UInt64 blockNumber,
      @JsonProperty("gasLimit") UInt64 gasLimit,
      @JsonProperty("gasUsed") UInt64 gasUsed,
      @JsonProperty("timestamp") UInt64 timestamp,
      @JsonProperty("extraData") Bytes extraData,
      @JsonProperty("baseFeePerGas") UInt256 baseFeePerGas,
      @JsonProperty("excessDataGas") UInt256 excessDataGas,
      @JsonProperty("blockHash") Bytes32 blockHash,
      @JsonProperty("transactions") List<Bytes> transactions,
      @JsonProperty("withdrawals") List<WithdrawalV1> withdrawals) {
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

  public static ExecutionPayloadV3 fromInternalExecutionPayload(
      final ExecutionPayload executionPayload) {
    final List<WithdrawalV1> withdrawalsList =
        getWithdrawals(executionPayload.getOptionalWithdrawals());
    return new ExecutionPayloadV3(
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
        executionPayload.toVersionDeneb().map(ExecutionPayloadDeneb::getExcessDataGas).orElse(null),
        executionPayload.getBlockHash(),
        executionPayload.getTransactions().stream()
            .map(SszByteListImpl::getBytes)
            .collect(Collectors.toList()),
        withdrawalsList);
  }

  @Override
  protected ExecutionPayloadBuilder applyToBuilder(
      final ExecutionPayloadSchema<?> executionPayloadSchema,
      final ExecutionPayloadBuilder builder) {
    return super.applyToBuilder(executionPayloadSchema, builder)
        .excessDataGas(
            () -> checkNotNull(excessDataGas, "Excess data gas not provided when required"));
  }
}
