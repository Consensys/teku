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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip4844;

import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BASE_FEE_PER_GAS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_NUMBER;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.EXCESS_DATA_GAS;
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
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema16;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;

public class ExecutionPayloadSchemaEip4844
    extends ContainerSchema16<
        ExecutionPayloadEip4844Impl,
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
        SszUInt256,
        SszBytes32,
        SszList<Transaction>,
        SszList<Withdrawal>>
    implements ExecutionPayloadSchema<ExecutionPayloadEip4844Impl> {

  private final ExecutionPayloadEip4844Impl defaultExecutionPayload;

  public ExecutionPayloadSchemaEip4844(final SpecConfigEip4844 specConfig) {
    super(
        "ExecutionPayloadEip4844",
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
        namedSchema(EXCESS_DATA_GAS, SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema(BLOCK_HASH, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(
            TRANSACTIONS,
            SszListSchema.create(
                new TransactionSchema(specConfig), specConfig.getMaxTransactionsPerPayload())),
        namedSchema(
            WITHDRAWALS,
            SszListSchema.create(
                new WithdrawalSchema(), specConfig.getMaxWithdrawalsPerPayload())));
    this.defaultExecutionPayload = createFromBackingNode(getDefaultTree());
  }

  public ExecutionPayload create(
      Bytes32 parentHash,
      Bytes20 feeRecipient,
      Bytes32 stateRoot,
      Bytes32 receiptsRoot,
      Bytes logsBloom,
      Bytes32 prevRandao,
      UInt64 blockNumber,
      UInt64 gasLimit,
      UInt64 gasUsed,
      UInt64 timestamp,
      Bytes extraData,
      UInt256 baseFeePerGas,
      UInt256 excessDataGas,
      Bytes32 blockHash,
      List<Bytes> transactions,
      List<Withdrawal> withdrawals) {
    return new ExecutionPayloadEip4844Impl(
        this,
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
        getExtraDataSchema().fromBytes(extraData),
        SszUInt256.of(baseFeePerGas),
        SszUInt256.of(excessDataGas),
        SszBytes32.of(blockHash),
        transactions.stream()
            .map(getTransactionSchema()::fromBytes)
            .collect(getTransactionsSchema().collector()),
        withdrawals.stream().collect(getWithdrawalsSchema().collector()));
  }

  @Override
  public ExecutionPayloadEip4844Impl getDefault() {
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

  public WithdrawalSchema getWithdrawalSchema() {
    return (WithdrawalSchema) getWithdrawalsSchema().getElementSchema();
  }

  @Override
  public LongList getBlindedNodeGeneralizedIndices() {
    return LongList.of(
        getChildGeneralizedIndex(getFieldIndex(TRANSACTIONS)),
        getChildGeneralizedIndex(getFieldIndex(WITHDRAWALS)));
  }

  @Override
  public ExecutionPayloadEip4844Impl createFromBackingNode(final TreeNode node) {
    return new ExecutionPayloadEip4844Impl(this, node);
  }

  private SszByteListSchema<?> getExtraDataSchema() {
    return (SszByteListSchema<?>) getChildSchema(getFieldIndex(EXTRA_DATA));
  }

  @SuppressWarnings("unchecked")
  private SszListSchema<Transaction, ?> getTransactionsSchema() {
    return (SszListSchema<Transaction, ?>) getChildSchema(getFieldIndex(TRANSACTIONS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Withdrawal, ?> getWithdrawalsSchema() {
    return (SszListSchema<Withdrawal, ?>) getChildSchema(getFieldIndex(WITHDRAWALS));
  }

  @Override
  public Optional<ExecutionPayloadSchemaEip4844> toVersionEip4844() {
    return Optional.of(this);
  }
}
