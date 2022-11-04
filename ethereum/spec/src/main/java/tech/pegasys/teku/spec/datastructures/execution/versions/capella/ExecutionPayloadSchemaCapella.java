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

package tech.pegasys.teku.spec.datastructures.execution.versions.capella;

import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BASE_FEE_PER_GAS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_NUMBER;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.EXTRA_DATA;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.FEE_RECIPIENT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.GAS_LIMIT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.GAS_USED;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.LOGS_BLOOM;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.PARENT_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.PREV_RANDAO;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.RECEIPTS_ROOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.STATE_ROOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.TIMESTAMP;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.TRANSACTIONS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.WITHDRAWALS;

import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema15;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;

public class ExecutionPayloadSchemaCapella
    extends ContainerSchema15<
        ExecutionPayloadCapellaImpl,
        SszBytes32,
        SszByteVector,
        SszBytes32,
        SszBytes32,
        SszByteVector,
        SszBytes32,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszByteList,
        SszUInt256,
        SszBytes32,
        SszList<Transaction>,
        SszList<Withdrawal>>
    implements ExecutionPayloadSchema<ExecutionPayloadCapellaImpl> {

  private final ExecutionPayloadCapellaImpl defaultExecutionPayload;

  public ExecutionPayloadSchemaCapella(final SpecConfigCapella specConfig) {
    super(
        "ExecutionPayloadCapella",
        namedSchema(PARENT_HASH, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(FEE_RECIPIENT, SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema(STATE_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(RECEIPTS_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(LOGS_BLOOM, SszByteVectorSchema.create(specConfig.getBytesPerLogsBloom())),
        namedSchema(PREV_RANDAO, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(BLOCK_NUMBER, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(GAS_LIMIT, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(GAS_USED, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(TIMESTAMP, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(EXTRA_DATA, SszByteListSchema.create(specConfig.getMaxExtraDataBytes())),
        namedSchema(BASE_FEE_PER_GAS, SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema(BLOCK_HASH, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(
            TRANSACTIONS,
            SszListSchema.create(
                new TransactionSchema(specConfig), specConfig.getMaxTransactionsPerPayload())),
        namedSchema(
            WITHDRAWALS,
            SszListSchema.create(
                new WithdrawalSchema(), specConfig.getMaxWithdrawalsPerPayload().longValue())));

    this.defaultExecutionPayload = createFromBackingNode(getDefaultTree());
  }

  @Override
  public ExecutionPayloadCapellaImpl getDefault() {
    return defaultExecutionPayload;
  }

  @Override
  public TransactionSchema getTransactionSchema() {
    return (TransactionSchema) getTransactionsSchema().getElementSchema();
  }

  @Override
  public long getBlindedNodeGeneralizedIndex() {
    return getChildGeneralizedIndex(getFieldIndex(TRANSACTIONS));
  }

  @Override
  public ExecutionPayloadCapellaImpl createFromBackingNode(TreeNode node) {
    return new ExecutionPayloadCapellaImpl(this, node);
  }

  @SuppressWarnings("unchecked")
  private SszListSchema<Transaction, ?> getTransactionsSchema() {
    return (SszListSchema<Transaction, ?>) getFieldSchema13();
  }
}
