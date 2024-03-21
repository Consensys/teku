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

import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BASE_FEE_PER_GAS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOB_GAS_USED;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_NUMBER;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.DEPOSIT_RECEIPTS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.EXCESS_BLOB_GAS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.EXITS;
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

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema19;
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
    extends ContainerSchema19<
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
        SszList<DepositReceipt>,
        SszList<ExecutionLayerExit>>
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
            DEPOSIT_RECEIPTS,
            SszListSchema.create(
                DepositReceipt.SSZ_SCHEMA, specConfig.getMaxDepositReceiptsPerPayload())),
        namedSchema(
            EXITS,
            SszListSchema.create(
                ExecutionLayerExit.SSZ_SCHEMA, specConfig.getMaxExecutionLayerExits())));
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
  public SszListSchema<DepositReceipt, ? extends SszList<DepositReceipt>>
      getDepositReceiptsSchemaRequired() {
    return getDepositReceiptsSchema();
  }

  @Override
  public DepositReceiptSchema getDepositReceiptSchemaRequired() {
    return getDepositReceiptSchema();
  }

  @Override
  public ExecutionLayerExitSchema getExecutionLayerExitSchemaRequired() {
    return getExecutionLayerExitSchema();
  }

  public WithdrawalSchema getWithdrawalSchema() {
    return (WithdrawalSchema) getWithdrawalsSchema().getElementSchema();
  }

  public DepositReceiptSchema getDepositReceiptSchema() {
    return (DepositReceiptSchema) getDepositReceiptsSchema().getElementSchema();
  }

  public ExecutionLayerExitSchema getExecutionLayerExitSchema() {
    return (ExecutionLayerExitSchema) getExecutionLayerExitsSchema().getElementSchema();
  }

  @Override
  public LongList getBlindedNodeGeneralizedIndices() {
    return LongList.of(
        getChildGeneralizedIndex(getFieldIndex(TRANSACTIONS)),
        getChildGeneralizedIndex(getFieldIndex(WITHDRAWALS)),
        getChildGeneralizedIndex(getFieldIndex(DEPOSIT_RECEIPTS)),
        getChildGeneralizedIndex(getFieldIndex(EXITS)));
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
  public SszListSchema<DepositReceipt, ?> getDepositReceiptsSchema() {
    return (SszListSchema<DepositReceipt, ?>) getChildSchema(getFieldIndex(DEPOSIT_RECEIPTS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<ExecutionLayerExit, ?> getExecutionLayerExitsSchema() {
    return (SszListSchema<ExecutionLayerExit, ?>) getChildSchema(getFieldIndex(EXITS));
  }
}
