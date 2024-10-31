/*
 * Copyright Consensys Software Inc., 2024
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

import static tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsFields.CONSOLIDATIONS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsFields.DEPOSITS;
import static tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsFields.WITHDRAWALS;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigElectra;

public class ExecutionRequestsSchema
    extends ContainerSchema3<
        ExecutionRequests,
        SszList<DepositRequest>,
        SszList<WithdrawalRequest>,
        SszList<ConsolidationRequest>> {

  public ExecutionRequestsSchema(final SpecConfigElectra specConfig) {
    super(
        "ExecutionRequests",
        namedSchema(
            DEPOSITS,
            SszListSchema.create(
                DepositRequest.SSZ_SCHEMA, specConfig.getMaxDepositRequestsPerPayload())),
        namedSchema(
            WITHDRAWALS,
            SszListSchema.create(
                WithdrawalRequest.SSZ_SCHEMA, specConfig.getMaxWithdrawalRequestsPerPayload())),
        namedSchema(
            CONSOLIDATIONS,
            SszListSchema.create(
                ConsolidationRequest.SSZ_SCHEMA,
                specConfig.getMaxConsolidationRequestsPerPayload())));
  }

  public ExecutionRequests create(
      final List<DepositRequest> deposits,
      final List<WithdrawalRequest> withdrawals,
      final List<ConsolidationRequest> consolidations) {
    return new ExecutionRequests(this, deposits, withdrawals, consolidations);
  }

  @Override
  public ExecutionRequests createFromBackingNode(final TreeNode node) {
    return new ExecutionRequests(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<DepositRequest, ?> getDepositRequestsSchema() {
    return (SszListSchema<DepositRequest, ?>) getChildSchema(getFieldIndex(DEPOSITS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<WithdrawalRequest, ?> getWithdrawalRequestsSchema() {
    return (SszListSchema<WithdrawalRequest, ?>) getChildSchema(getFieldIndex(WITHDRAWALS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<ConsolidationRequest, ?> getConsolidationRequestsSchema() {
    return (SszListSchema<ConsolidationRequest, ?>) getChildSchema(getFieldIndex(CONSOLIDATIONS));
  }
}
