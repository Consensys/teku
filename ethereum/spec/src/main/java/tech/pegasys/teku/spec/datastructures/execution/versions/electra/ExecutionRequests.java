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

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/*
 * <spec ssz_object="ExecutionRequests" fork="electra">
 * class ExecutionRequests(Container):
 *     deposits: List[DepositRequest, MAX_DEPOSIT_REQUESTS_PER_PAYLOAD]  # [New in Electra:EIP6110]
 *     withdrawals: List[WithdrawalRequest, MAX_WITHDRAWAL_REQUESTS_PER_PAYLOAD]  # [New in Electra:EIP7002:EIP7251]
 *     consolidations: List[ConsolidationRequest, MAX_CONSOLIDATION_REQUESTS_PER_PAYLOAD]  # [New in Electra:EIP7251]
 * </spec>
 */
public class ExecutionRequests
    extends Container3<
        ExecutionRequests,
        SszList<DepositRequest>,
        SszList<WithdrawalRequest>,
        SszList<ConsolidationRequest>> {

  ExecutionRequests(
      final ExecutionRequestsSchema schema,
      final List<DepositRequest> deposits,
      final List<WithdrawalRequest> withdrawals,
      final List<ConsolidationRequest> consolidations) {
    super(
        schema,
        schema.getDepositRequestsSchema().createFromElements(deposits),
        schema.getWithdrawalRequestsSchema().createFromElements(withdrawals),
        schema.getConsolidationRequestsSchema().createFromElements(consolidations));
  }

  ExecutionRequests(final ExecutionRequestsSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public List<DepositRequest> getDeposits() {
    return getField0().stream().toList();
  }

  public List<WithdrawalRequest> getWithdrawals() {
    return getField1().stream().toList();
  }

  public List<ConsolidationRequest> getConsolidations() {
    return getField2().stream().toList();
  }

  @Override
  public ExecutionRequestsSchema getSchema() {
    return (ExecutionRequestsSchema) super.getSchema();
  }
}
