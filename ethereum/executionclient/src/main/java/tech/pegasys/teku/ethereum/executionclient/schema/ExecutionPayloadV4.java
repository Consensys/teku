/*
 * Copyright Consensys Software Inc., 2022
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
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerWithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadElectra;

public class ExecutionPayloadV4 extends ExecutionPayloadV3 {

  public final List<DepositRequestV1> depositRequests;
  public final List<WithdrawalRequestV1> withdrawalRequests;
  public final List<ConsolidationRequestV1> consolidationRequests;

  public ExecutionPayloadV4(
      final @JsonProperty("parentHash") Bytes32 parentHash,
      final @JsonProperty("feeRecipient") Bytes20 feeRecipient,
      final @JsonProperty("stateRoot") Bytes32 stateRoot,
      final @JsonProperty("receiptsRoot") Bytes32 receiptsRoot,
      final @JsonProperty("logsBloom") Bytes logsBloom,
      final @JsonProperty("prevRandao") Bytes32 prevRandao,
      final @JsonProperty("blockNumber") UInt64 blockNumber,
      final @JsonProperty("gasLimit") UInt64 gasLimit,
      final @JsonProperty("gasUsed") UInt64 gasUsed,
      final @JsonProperty("timestamp") UInt64 timestamp,
      final @JsonProperty("extraData") Bytes extraData,
      final @JsonProperty("baseFeePerGas") UInt256 baseFeePerGas,
      final @JsonProperty("blockHash") Bytes32 blockHash,
      final @JsonProperty("transactions") List<Bytes> transactions,
      final @JsonProperty("withdrawals") List<WithdrawalV1> withdrawals,
      final @JsonProperty("blobGasUsed") UInt64 blobGasUsed,
      final @JsonProperty("excessBlobGas") UInt64 excessBlobGas,
      final @JsonProperty("depositRequests") List<DepositRequestV1> depositRequests,
      final @JsonProperty("withdrawalRequests") List<WithdrawalRequestV1> withdrawalRequests,
      final @JsonProperty("consolidationRequests") List<ConsolidationRequestV1>
              consolidationRequests) {
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
        withdrawals,
        blobGasUsed,
        excessBlobGas);
    checkNotNull(depositRequests, "depositRequests");
    checkNotNull(withdrawalRequests, "withdrawalRequests");
    checkNotNull(consolidationRequests, "consolidationRequests");
    this.depositRequests = depositRequests;
    this.withdrawalRequests = withdrawalRequests;
    this.consolidationRequests = consolidationRequests;
  }

  @Override
  protected ExecutionPayloadBuilder applyToBuilder(
      final ExecutionPayloadSchema<?> executionPayloadSchema,
      final ExecutionPayloadBuilder builder) {
    return super.applyToBuilder(executionPayloadSchema, builder)
        .depositReceipts(
            () ->
                depositRequests.stream()
                    .map(
                        depositRequestV1 ->
                            createInternalDepositReceipt(depositRequestV1, executionPayloadSchema))
                    .toList())
        .withdrawalRequests(
            () ->
                withdrawalRequests.stream()
                    .map(
                        withdrawalRequestV1 ->
                            createInternalWithdrawalRequest(
                                withdrawalRequestV1, executionPayloadSchema))
                    .toList())
        .consolidationRequests(
            () ->
                consolidationRequests.stream()
                    .map(
                        consolidationRequestV1 ->
                            createInternalConsolidationRequest(
                                consolidationRequestV1, executionPayloadSchema))
                    .toList());
  }

  private DepositReceipt createInternalDepositReceipt(
      final DepositRequestV1 depositRequestV1,
      final ExecutionPayloadSchema<?> executionPayloadSchema) {
    return executionPayloadSchema
        .getDepositReceiptSchemaRequired()
        .create(
            BLSPublicKey.fromBytesCompressed(depositRequestV1.pubkey),
            depositRequestV1.withdrawalCredentials,
            depositRequestV1.amount,
            BLSSignature.fromBytesCompressed(depositRequestV1.signature),
            depositRequestV1.index);
  }

  private ExecutionLayerWithdrawalRequest createInternalWithdrawalRequest(
      final WithdrawalRequestV1 withdrawalRequestV1,
      final ExecutionPayloadSchema<?> executionPayloadSchema) {
    return executionPayloadSchema
        .getExecutionLayerWithdrawalRequestSchemaRequired()
        .create(
            withdrawalRequestV1.sourceAddress,
            BLSPublicKey.fromBytesCompressed(withdrawalRequestV1.validatorPubkey),
            withdrawalRequestV1.amount);
  }

  private ConsolidationRequest createInternalConsolidationRequest(
      final ConsolidationRequestV1 consolidationRequestV1,
      final ExecutionPayloadSchema<?> executionPayloadSchema) {
    return executionPayloadSchema
        .getConsolidationSchemaRequired()
        .create(
            consolidationRequestV1.sourceAddress,
            BLSPublicKey.fromBytesCompressed(consolidationRequestV1.sourcePubkey),
            BLSPublicKey.fromBytesCompressed(consolidationRequestV1.targetPubkey));
  }

  public static ExecutionPayloadV4 fromInternalExecutionPayload(
      final ExecutionPayload executionPayload) {
    return new ExecutionPayloadV4(
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
        getTransactions(executionPayload),
        getWithdrawals(ExecutionPayloadCapella.required(executionPayload).getWithdrawals()),
        ExecutionPayloadDeneb.required(executionPayload).getBlobGasUsed(),
        ExecutionPayloadDeneb.required(executionPayload).getExcessBlobGas(),
        getDepositRequests(ExecutionPayloadElectra.required(executionPayload).getDepositReceipts()),
        getWithdrawalRequests(
            ExecutionPayloadElectra.required(executionPayload).getWithdrawalRequests()),
        getConsolidationRequests(
            ExecutionPayloadElectra.required(executionPayload).getConsolidationRequests()));
  }

  public static List<DepositRequestV1> getDepositRequests(
      final SszList<DepositReceipt> depositReceipts) {
    return depositReceipts.stream()
        .map(
            depositReceipt ->
                new DepositRequestV1(
                    depositReceipt.getPubkey().toBytesCompressed(),
                    depositReceipt.getWithdrawalCredentials(),
                    depositReceipt.getAmount(),
                    depositReceipt.getSignature().toBytesCompressed(),
                    depositReceipt.getIndex()))
        .toList();
  }

  public static List<WithdrawalRequestV1> getWithdrawalRequests(
      final SszList<ExecutionLayerWithdrawalRequest> withdrawalRequests) {
    return withdrawalRequests.stream()
        .map(
            withdrawalRequest ->
                new WithdrawalRequestV1(
                    withdrawalRequest.getSourceAddress(),
                    withdrawalRequest.getValidatorPublicKey().toBytesCompressed(),
                    withdrawalRequest.getAmount()))
        .toList();
  }

  public static List<ConsolidationRequestV1> getConsolidationRequests(
      final SszList<ConsolidationRequest> consolidationRequests) {
    return consolidationRequests.stream()
        .map(
            consolidationRequest ->
                new ConsolidationRequestV1(
                    consolidationRequest.getSourceAddress(),
                    consolidationRequest.getSourcePubkey().toBytesCompressed(),
                    consolidationRequest.getTargetPubkey().toBytesCompressed()))
        .toList();
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
    final ExecutionPayloadV4 that = (ExecutionPayloadV4) o;
    return Objects.equals(depositRequests, that.depositRequests)
        && Objects.equals(withdrawalRequests, that.withdrawalRequests)
        && Objects.equals(consolidationRequests, that.consolidationRequests);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), depositRequests, withdrawalRequests, consolidationRequests);
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
        .add("blobGasUsed", blobGasUsed)
        .add("excessBlobGas", excessBlobGas)
        .add("depositRequests", depositRequests)
        .add("withdrawalRequests", withdrawalRequests)
        .add("consolidationRequests", consolidationRequests)
        .toString();
  }
}
