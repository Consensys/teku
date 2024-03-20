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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteListImpl;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerExit;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadElectra;

public class ExecutionPayloadV4 extends ExecutionPayloadV3 {
  public final List<DepositReceiptV1> depositReceipts;
  public final List<ExitV1> exits;

  public ExecutionPayloadV4(
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
      @JsonProperty("withdrawals") List<WithdrawalV1> withdrawals,
      @JsonProperty("blobGasUsed") UInt64 blobGasUsed,
      @JsonProperty("excessBlobGas") UInt64 excessBlobGas,
      @JsonProperty("depositReceipts") List<DepositReceiptV1> depositReceipts,
      @JsonProperty("exits") List<ExitV1> exits) {
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
    this.depositReceipts = depositReceipts;
    this.exits = exits;
  }

  public static ExecutionPayloadV4 fromInternalExecutionPayload(
      final ExecutionPayload executionPayload) {
    final List<WithdrawalV1> withdrawalsList =
        getWithdrawals(executionPayload.getOptionalWithdrawals());
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
        executionPayload.getTransactions().stream().map(SszByteListImpl::getBytes).toList(),
        withdrawalsList,
        executionPayload.toVersionDeneb().map(ExecutionPayloadDeneb::getBlobGasUsed).orElse(null),
        executionPayload.toVersionDeneb().map(ExecutionPayloadDeneb::getExcessBlobGas).orElse(null),
        getDepositReceipts(
            executionPayload.toVersionElectra().map(ExecutionPayloadElectra::getDepositReceipts)),
        getExits(executionPayload.toVersionElectra().map(ExecutionPayloadElectra::getExits)));
  }

  @Override
  protected ExecutionPayloadBuilder applyToBuilder(
      final ExecutionPayloadSchema<?> executionPayloadSchema,
      final ExecutionPayloadBuilder builder) {
    return super.applyToBuilder(executionPayloadSchema, builder)
        .depositReceipts(
            () ->
                checkNotNull(depositReceipts, "depositReceipts not provided when required").stream()
                    .map(
                        depositReceiptV1 ->
                            createInternalDepositReceipt(depositReceiptV1, executionPayloadSchema))
                    .toList())
        .exits(
            () ->
                checkNotNull(exits, "exits not provided when required").stream()
                    .map(exitV1 -> createInternalExit(exitV1, executionPayloadSchema))
                    .toList());
  }

  private DepositReceipt createInternalDepositReceipt(
      final DepositReceiptV1 depositReceiptV1,
      final ExecutionPayloadSchema<?> executionPayloadSchema) {
    return executionPayloadSchema
        .getDepositReceiptSchemaRequired()
        .create(
            BLSPublicKey.fromBytesCompressed(depositReceiptV1.pubkey),
            depositReceiptV1.withdrawalCredentials,
            depositReceiptV1.amount,
            BLSSignature.fromBytesCompressed(depositReceiptV1.signature),
            depositReceiptV1.index);
  }

  private ExecutionLayerExit createInternalExit(
      final ExitV1 exitV1, final ExecutionPayloadSchema<?> executionPayloadSchema) {
    return executionPayloadSchema
        .getExecutionLayerExitSchemaRequired()
        .create(exitV1.sourceAddress, BLSPublicKey.fromBytesCompressed(exitV1.validatorPublicKey));
  }

  public static List<DepositReceiptV1> getDepositReceipts(
      final Optional<SszList<DepositReceipt>> maybeDepositReceipts) {
    if (maybeDepositReceipts.isEmpty()) {
      return List.of();
    }

    final List<DepositReceiptV1> depositReceipts = new ArrayList<>();

    for (DepositReceipt depositReceipt : maybeDepositReceipts.get()) {
      depositReceipts.add(
          new DepositReceiptV1(
              depositReceipt.getPubkey().toBytesCompressed(),
              depositReceipt.getWithdrawalCredentials(),
              depositReceipt.getAmount(),
              depositReceipt.getSignature().toBytesCompressed(),
              depositReceipt.getIndex()));
    }
    return depositReceipts;
  }

  public static List<ExitV1> getExits(final Optional<SszList<ExecutionLayerExit>> maybeExits) {
    if (maybeExits.isEmpty()) {
      return List.of();
    }

    final List<ExitV1> exits = new ArrayList<>();

    for (ExecutionLayerExit exit : maybeExits.get()) {
      exits.add(
          new ExitV1(exit.getSourceAddress(), exit.getValidatorPublicKey().toBytesCompressed()));
    }
    return exits;
  }
}
