/*
 * Copyright Consensys Software Inc., 2026
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
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.CONSOLIDATION_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.DEPOSIT_REQUEST_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.WITHDRAWAL_REQUEST_SCHEMA;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class ExecutionRequestsSchemaElectra
    extends ContainerSchema3<
        ExecutionRequestsElectra,
        SszList<DepositRequest>,
        SszList<WithdrawalRequest>,
        SszList<ConsolidationRequest>>
    implements ExecutionRequestsSchema<ExecutionRequestsElectra> {

  public ExecutionRequestsSchemaElectra(
      final SpecConfigElectra specConfig,
      final SchemaRegistry schemaRegistry,
      final String containerName) {
    super(
        containerName,
        namedSchema(
            DEPOSITS,
            SszListSchema.create(
                schemaRegistry.get(DEPOSIT_REQUEST_SCHEMA),
                specConfig.getMaxDepositRequestsPerPayload())),
        namedSchema(
            WITHDRAWALS,
            SszListSchema.create(
                schemaRegistry.get(WITHDRAWAL_REQUEST_SCHEMA),
                specConfig.getMaxWithdrawalRequestsPerPayload())),
        namedSchema(
            CONSOLIDATIONS,
            SszListSchema.create(
                schemaRegistry.get(CONSOLIDATION_REQUEST_SCHEMA),
                specConfig.getMaxConsolidationRequestsPerPayload())));
  }

  public ExecutionRequestsElectra create(
      final List<DepositRequest> deposits,
      final List<WithdrawalRequest> withdrawals,
      final List<ConsolidationRequest> consolidations) {
    return new ExecutionRequestsElectra(this, deposits, withdrawals, consolidations);
  }

  @Override
  public ExecutionRequestsElectra createFromBackingNode(final TreeNode node) {
    return new ExecutionRequestsElectra(this, node);
  }

  @Override
  public ExecutionRequestsBuilder createBuilder() {
    return new ExecutionRequestsBuilderElectra(this);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<DepositRequest, ?> getDepositRequestsSchema() {
    return (SszListSchema<DepositRequest, ?>) getChildSchema(getFieldIndex(DEPOSITS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<WithdrawalRequest, ?> getWithdrawalRequestsSchema() {
    return (SszListSchema<WithdrawalRequest, ?>) getChildSchema(getFieldIndex(WITHDRAWALS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<ConsolidationRequest, ?> getConsolidationRequestsSchema() {
    return (SszListSchema<ConsolidationRequest, ?>) getChildSchema(getFieldIndex(CONSOLIDATIONS));
  }
}
