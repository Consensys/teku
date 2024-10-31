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

package tech.pegasys.teku.api.schema.electra;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequestSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequestSchema;

public class ExecutionRequests {

  @JsonProperty("deposits")
  private final List<DepositRequest> deposits;

  @JsonProperty("withdrawals")
  private final List<WithdrawalRequest> withdrawals;

  @JsonProperty("consolidations")
  private final List<ConsolidationRequest> consolidations;

  public ExecutionRequests(
      @JsonProperty("deposits") final List<DepositRequest> deposits,
      @JsonProperty("withdrawals") final List<WithdrawalRequest> withdrawals,
      @JsonProperty("consolidations") final List<ConsolidationRequest> consolidations) {
    this.deposits = deposits;
    this.withdrawals = withdrawals;
    this.consolidations = consolidations;
  }

  public ExecutionRequests(
      final tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests
          executionRequests) {
    this.deposits = executionRequests.getDeposits().stream().map(DepositRequest::new).toList();
    this.withdrawals =
        executionRequests.getWithdrawals().stream().map(WithdrawalRequest::new).toList();
    this.consolidations =
        executionRequests.getConsolidations().stream().map(ConsolidationRequest::new).toList();
  }

  public final tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests
      asInternalConsolidationRequest(final ExecutionRequestsSchema schema) {

    final DepositRequestSchema depositSchema =
        (DepositRequestSchema) schema.getDepositRequestsSchema().getElementSchema();
    final WithdrawalRequestSchema withdrawalSchema =
        (WithdrawalRequestSchema) schema.getWithdrawalRequestsSchema().getElementSchema();
    final ConsolidationRequestSchema consolidationSchema =
        (ConsolidationRequestSchema) schema.getConsolidationRequestsSchema().getElementSchema();

    final List<tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest>
        depositsInternal =
            deposits.stream()
                .map(depositRequest -> depositRequest.asInternalDepositRequest(depositSchema))
                .toList();
    final List<tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest>
        withdrawalsInternal =
            withdrawals.stream()
                .map(
                    withdrawalRequest ->
                        withdrawalRequest.asInternalWithdrawalRequest(withdrawalSchema))
                .toList();
    final List<
            tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest>
        consolidationsInternal =
            consolidations.stream()
                .map(
                    consolidationRequest ->
                        consolidationRequest.asInternalConsolidationRequest(consolidationSchema))
                .toList();
    return schema.create(depositsInternal, withdrawalsInternal, consolidationsInternal);
  }
}
