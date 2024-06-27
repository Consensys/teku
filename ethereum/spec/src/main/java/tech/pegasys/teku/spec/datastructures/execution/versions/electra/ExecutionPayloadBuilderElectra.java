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

package tech.pegasys.teku.spec.datastructures.execution.versions.electra;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadBuilderDeneb;

public class ExecutionPayloadBuilderElectra extends ExecutionPayloadBuilderDeneb {
  private ExecutionPayloadSchemaElectra schema;

  protected List<DepositRequest> depositRequests;
  protected List<WithdrawalRequest> withdrawalRequests;
  protected List<ConsolidationRequest> consolidationRequests;

  public ExecutionPayloadBuilderElectra schema(final ExecutionPayloadSchemaElectra schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public ExecutionPayloadBuilder depositRequests(
      final Supplier<List<DepositRequest>> depositRequestsSupplier) {
    this.depositRequests = depositRequestsSupplier.get();
    return this;
  }

  @Override
  public ExecutionPayloadBuilder withdrawalRequests(
      final Supplier<List<WithdrawalRequest>> withdrawalRequestsSupplier) {
    this.withdrawalRequests = withdrawalRequestsSupplier.get();
    return this;
  }

  @Override
  public ExecutionPayloadBuilder consolidationRequests(
      final Supplier<List<ConsolidationRequest>> consolidationRequestsSupplier) {
    this.consolidationRequests = consolidationRequestsSupplier.get();
    return this;
  }

  @Override
  protected void validateSchema() {
    checkNotNull(schema, "schema must be specified");
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(depositRequests, "depositRequests must be specified");
    checkNotNull(withdrawalRequests, "withdrawalRequests must be specified");
    checkNotNull(consolidationRequests, "consolidationRequests must be specified");
  }

  @Override
  public ExecutionPayload build() {
    validate();
    return new ExecutionPayloadElectraImpl(
        schema,
        SszBytes32.of(parentHash),
        SszByteVector.fromBytes(feeRecipient.getWrappedBytes()),
        SszBytes32.of(stateRoot),
        SszBytes32.of(receiptsRoot),
        SszByteVector.fromBytes(logsBloom),
        SszBytes32.of(prevRandao),
        SszUInt64.of(blockNumber),
        SszUInt64.of(gasLimit),
        SszUInt64.of(gasUsed),
        SszUInt64.of(timestamp),
        schema.getExtraDataSchema().fromBytes(extraData),
        SszUInt256.of(baseFeePerGas),
        SszBytes32.of(blockHash),
        transactions.stream()
            .map(schema.getTransactionSchema()::fromBytes)
            .collect(schema.getTransactionsSchema().collector()),
        schema.getWithdrawalsSchema().createFromElements(withdrawals),
        SszUInt64.of(blobGasUsed),
        SszUInt64.of(excessBlobGas),
        schema.getDepositRequestsSchema().createFromElements(depositRequests),
        schema.getWithdrawalRequestsSchema().createFromElements(withdrawalRequests),
        schema.getConsolidationRequestsSchema().createFromElements(consolidationRequests));
  }
}
