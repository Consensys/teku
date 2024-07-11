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

import static tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.namedSchema;
import static tech.pegasys.teku.spec.datastructures.StableContainerCapacities.MAX_EXECUTION_PAYLOAD_FIELDS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BASE_FEE_PER_GAS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOB_GAS_USED;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_NUMBER;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.CONSOLIDATION_REQUESTS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.DEPOSIT_REQUESTS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.EXCESS_BLOB_GAS;
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
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.WITHDRAWAL_REQUESTS;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ProfileSchema20;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;

public class ExecutionPayloadSchemaElectra
    extends ProfileSchema20<
        ExecutionPayloadElectraImpl,
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
        SszList<Withdrawal>,
        SszUInt64,
        SszUInt64,
        SszList<DepositRequest>,
        SszList<WithdrawalRequest>,
        SszList<ConsolidationRequest>>
    implements ExecutionPayloadSchema<ExecutionPayloadElectraImpl> {

  private final ExecutionPayloadElectraImpl defaultExecutionPayload;

  public ExecutionPayloadSchemaElectra(final SpecConfigElectra specConfig) {
    super(
        "ExecutionPayloadElectra",
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
            SszListSchema.create(Withdrawal.SSZ_SCHEMA, specConfig.getMaxWithdrawalsPerPayload())),
        namedSchema(BLOB_GAS_USED, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(EXCESS_BLOB_GAS, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(
            DEPOSIT_REQUESTS,
            SszListSchema.create(
                DepositRequest.SSZ_SCHEMA, specConfig.getMaxDepositRequestsPerPayload())),
        namedSchema(
            WITHDRAWAL_REQUESTS,
            SszListSchema.create(
                WithdrawalRequest.SSZ_SCHEMA, specConfig.getMaxWithdrawalRequestsPerPayload())),
        namedSchema(
            CONSOLIDATION_REQUESTS,
            SszListSchema.create(
                ConsolidationRequest.SSZ_SCHEMA,
                specConfig.getMaxConsolidationRequestsPerPayload())),
        MAX_EXECUTION_PAYLOAD_FIELDS);
    this.defaultExecutionPayload = createFromBackingNode(getDefaultTree());
  }

  @Override
  public ExecutionPayloadElectraImpl getDefault() {
    return defaultExecutionPayload;
  }

  @Override
  public TransactionSchema getTransactionSchema() {
    return (TransactionSchema) getTransactionsSchema().getElementSchema();
  }

  @Override
  public SszListSchema<Withdrawal, ? extends SszList<Withdrawal>> getWithdrawalsSchemaRequired() {
    return getWithdrawalsSchema();
  }

  @Override
  public WithdrawalSchema getWithdrawalSchemaRequired() {
    return getWithdrawalSchema();
  }

  @Override
  public SszListSchema<DepositRequest, ? extends SszList<DepositRequest>>
      getDepositRequestsSchemaRequired() {
    return getDepositRequestsSchema();
  }

  @Override
  public DepositRequestSchema getDepositRequestSchemaRequired() {
    return getDepositRequestSchema();
  }

  @Override
  public SszListSchema<WithdrawalRequest, ? extends SszList<WithdrawalRequest>>
      getWithdrawalRequestsSchemaRequired() {
    return getWithdrawalRequestsSchema();
  }

  @Override
  public WithdrawalRequestSchema getWithdrawalRequestSchemaRequired() {
    return getWithdrawalRequestSchema();
  }

  @Override
  public ConsolidationRequestSchema getConsolidationRequestSchemaRequired() {
    return getConsolidationRequestSchema();
  }

  @Override
  public SszListSchema<ConsolidationRequest, ? extends SszList<ConsolidationRequest>>
      getConsolidationRequestsSchemaRequired() {
    return getConsolidationRequestsSchema();
  }

  public WithdrawalSchema getWithdrawalSchema() {
    return (WithdrawalSchema) getWithdrawalsSchema().getElementSchema();
  }

  public DepositRequestSchema getDepositRequestSchema() {
    return (DepositRequestSchema) getDepositRequestsSchema().getElementSchema();
  }

  public WithdrawalRequestSchema getWithdrawalRequestSchema() {
    return (WithdrawalRequestSchema) getWithdrawalRequestsSchema().getElementSchema();
  }

  public ConsolidationRequestSchema getConsolidationRequestSchema() {
    return (ConsolidationRequestSchema) getConsolidationRequestsSchema().getElementSchema();
  }

  @Override
  public LongList getBlindedNodeGeneralizedIndices() {
    return LongList.of(
        getChildGeneralizedIndex(getFieldIndex(TRANSACTIONS)),
        getChildGeneralizedIndex(getFieldIndex(WITHDRAWALS)),
        getChildGeneralizedIndex(getFieldIndex(DEPOSIT_REQUESTS)),
        getChildGeneralizedIndex(getFieldIndex(WITHDRAWAL_REQUESTS)),
        getChildGeneralizedIndex(getFieldIndex(CONSOLIDATION_REQUESTS)));
  }

  @Override
  public ExecutionPayload createExecutionPayload(
      final Consumer<ExecutionPayloadBuilder> builderConsumer) {
    final ExecutionPayloadBuilderElectra builder =
        new ExecutionPayloadBuilderElectra().schema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  @Override
  public ExecutionPayloadElectraImpl createFromBackingNode(final TreeNode node) {
    return new ExecutionPayloadElectraImpl(this, node);
  }

  public SszByteListSchema<?> getExtraDataSchema() {
    return (SszByteListSchema<?>) getChildSchema(getFieldIndex(EXTRA_DATA));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Transaction, ?> getTransactionsSchema() {
    return (SszListSchema<Transaction, ?>) getChildSchema(getFieldIndex(TRANSACTIONS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Withdrawal, ?> getWithdrawalsSchema() {
    return (SszListSchema<Withdrawal, ?>) getChildSchema(getFieldIndex(WITHDRAWALS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<DepositRequest, ?> getDepositRequestsSchema() {
    return (SszListSchema<DepositRequest, ?>) getChildSchema(getFieldIndex(DEPOSIT_REQUESTS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<WithdrawalRequest, ?> getWithdrawalRequestsSchema() {
    return (SszListSchema<WithdrawalRequest, ?>) getChildSchema(getFieldIndex(WITHDRAWAL_REQUESTS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<ConsolidationRequest, ?> getConsolidationRequestsSchema() {
    return (SszListSchema<ConsolidationRequest, ?>)
        getChildSchema(getFieldIndex(CONSOLIDATION_REQUESTS));
  }
}
