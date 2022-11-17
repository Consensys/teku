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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteListImpl;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.EthConstants;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;

public class ExecutionPayloadV2 extends ExecutionPayloadV1 {
  public final List<WithdrawalV1> withdrawals;

  public ExecutionPayloadV2(
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
        transactions);
    this.withdrawals = withdrawals;
  }

  public static ExecutionPayloadV2 fromInternalExecutionPayload(ExecutionPayload executionPayload) {
    List<WithdrawalV1> withdrawalsList = getWithdrawals(executionPayload.getOptionalWithdrawals());
    return new ExecutionPayloadV2(
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
        executionPayload.getBlockHash(),
        executionPayload.getTransactions().stream()
            .map(SszByteListImpl::getBytes)
            .collect(Collectors.toList()),
        withdrawalsList);
  }

  @Override
  public ExecutionPayload asInternalExecutionPayload(
      ExecutionPayloadSchema<?> executionPayloadSchema) {
    return executionPayloadSchema
        .toVersionCapella()
        .orElseThrow()
        .create(
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
            withdrawals.stream()
                .map(withdrawalV1 -> createInternalWithdrawal(withdrawalV1, executionPayloadSchema))
                .collect(Collectors.toList()));
  }

  private Withdrawal createInternalWithdrawal(
      final WithdrawalV1 withdrawalV1, ExecutionPayloadSchema<?> executionPayloadSchema) {
    return executionPayloadSchema
        .toVersionCapella()
        .orElseThrow()
        .getWithdrawalSchema()
        .create(
            withdrawalV1.index,
            withdrawalV1.validatorIndex,
            withdrawalV1.address,
            EthConstants.weiToGwei(withdrawalV1.amount));
  }

  public static List<WithdrawalV1> getWithdrawals(
      final Optional<SszList<Withdrawal>> maybeWithdrawals) {
    if (maybeWithdrawals.isEmpty()) {
      return List.of();
    }

    final List<WithdrawalV1> withdrawals = new ArrayList<>();

    for (Withdrawal w : maybeWithdrawals.get()) {
      withdrawals.add(
          new WithdrawalV1(
              w.getIndex(),
              w.getValidatorIndex(),
              w.getAddress(),
              EthConstants.gweiToWei(w.getAmount())));
    }
    return withdrawals;
  }
}
