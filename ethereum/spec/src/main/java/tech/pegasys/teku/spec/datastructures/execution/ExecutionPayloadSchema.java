/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.execution;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema14;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigMerge;

public class ExecutionPayloadSchema
    extends ContainerSchema14<
        ExecutionPayload,
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
        SszList<Transaction>> {

  public ExecutionPayloadSchema(final SpecConfigMerge specConfig) {
    super(
        "ExecutionPayload",
        namedSchema("parent_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("fee_recipient", SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema("state_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("receipt_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("logs_bloom", SszByteVectorSchema.create(specConfig.getBytesPerLogsBloom())),
        namedSchema("random", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("block_number", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("gas_limit", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("gas_used", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("timestamp", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("extra_data", SszByteListSchema.create(specConfig.getMaxExtraDataBytes())),
        namedSchema("base_fee_per_gas", SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(
            "transactions",
            SszListSchema.create(
                new TransactionSchema(specConfig), specConfig.getMaxTransactionsPerPayload())));
  }

  public ExecutionPayload create(
      Bytes32 parentHash,
      Bytes20 feeRecipient,
      Bytes32 stateRoot,
      Bytes32 receiptRoot,
      Bytes logsBloom,
      Bytes32 random,
      UInt64 blockNumber,
      UInt64 gasLimit,
      UInt64 gasUsed,
      UInt64 timestamp,
      Bytes extraData,
      UInt256 baseFeePerGas,
      Bytes32 blockHash,
      List<Bytes> transactions) {
    return new ExecutionPayload(
        this,
        SszBytes32.of(parentHash),
        SszByteVector.fromBytes(feeRecipient.getWrappedBytes()),
        SszBytes32.of(stateRoot),
        SszBytes32.of(receiptRoot),
        SszByteVector.fromBytes(logsBloom),
        SszBytes32.of(random),
        SszUInt64.of(blockNumber),
        SszUInt64.of(gasLimit),
        SszUInt64.of(gasUsed),
        SszUInt64.of(timestamp),
        getExtraDataSchema().fromBytes(extraData),
        SszUInt256.of(baseFeePerGas),
        SszBytes32.of(blockHash),
        transactions.stream()
            .map(getTransactionSchema()::fromBytes)
            .collect(getTransactionsSchema().collector()));
  }

  @SuppressWarnings("unchecked")
  private TransactionSchema getTransactionSchema() {
    return (TransactionSchema) getTransactionsSchema().getElementSchema();
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Transaction, ?> getTransactionsSchema() {
    return (SszListSchema<Transaction, ?>) getFieldSchema13();
  }

  public SszByteListSchema<?> getExtraDataSchema() {
    return (SszByteListSchema<?>) getFieldSchema10();
  }

  @Override
  public ExecutionPayload createFromBackingNode(TreeNode node) {
    return new ExecutionPayload(this, node);
  }
}
