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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.collections.SszByteVector;
import tech.pegasys.teku.ssz.containers.Container14;
import tech.pegasys.teku.ssz.containers.ContainerSchema14;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt256;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.type.Bytes20;

public class ExecutionPayload
    extends Container14<
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

  public static class ExecutionPayloadSchema
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

    public ExecutionPayloadSchema() {
      super(
          "ExecutionPayload",
          namedSchema("parent_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("coinbase", SszByteVectorSchema.create(Bytes20.SIZE)),
          namedSchema("state_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("receipt_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("logs_bloom", SszByteVectorSchema.create(SpecConfig.BYTES_PER_LOGS_BLOOM)),
          namedSchema("random", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("block_number", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("gas_limit", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("gas_used", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("timestamp", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("extra_data", SszByteListSchema.create(SpecConfig.MAX_EXTRA_DATA_BYTES)),
          namedSchema("base_fee_per_gas", SszPrimitiveSchemas.UINT256_SCHEMA),
          namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema(
              "transactions",
              SszListSchema.create(Transaction.SSZ_SCHEMA, SpecConfig.MAX_EXECUTION_TRANSACTIONS)));
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

  public static final ExecutionPayloadSchema SSZ_SCHEMA = new ExecutionPayloadSchema();

  public ExecutionPayload() {
    super(SSZ_SCHEMA);
  }

  ExecutionPayload(
      ContainerSchema14<
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
              SszList<Transaction>>
          type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public ExecutionPayload(
      Bytes32 parentHash,
      Bytes20 coinbase,
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
    super(
        SSZ_SCHEMA,
        SszBytes32.of(parentHash),
        SszByteVector.fromBytes(coinbase.getWrappedBytes()),
        SszBytes32.of(stateRoot),
        SszBytes32.of(receiptRoot),
        SszByteVector.fromBytes(logsBloom),
        SszBytes32.of(random),
        SszUInt64.of(blockNumber),
        SszUInt64.of(gasLimit),
        SszUInt64.of(gasUsed),
        SszUInt64.of(timestamp),
        SSZ_SCHEMA.getExtraDataSchema().fromBytes(extraData),
        SszUInt256.of(baseFeePerGas),
        SszBytes32.of(blockHash),
        transactions.stream()
            .map(txBytes -> Transaction.SSZ_SCHEMA.getOpaqueTransactionSchema().fromBytes(txBytes))
            .map(Transaction.SSZ_SCHEMA::createOpaque)
            .collect(SSZ_SCHEMA.getTransactionsSchema().collector()));
  }

  @Override
  public ExecutionPayloadSchema getSchema() {
    return SSZ_SCHEMA;
  }

  public Bytes32 getParentHash() {
    return getField0().get();
  }

  public Bytes20 getCoinbase() {
    return Bytes20.leftPad(getField1().getBytes());
  }

  public Bytes32 getStateRoot() {
    return getField2().get();
  }

  public Bytes32 getReceiptRoot() {
    return getField3().get();
  }

  public Bytes getLogsBloom() {
    return getField4().getBytes();
  }

  public Bytes32 getRandom() {
    return getField5().get();
  }

  public UInt64 getBlockNumber() {
    return getField6().get();
  }

  public UInt64 getGasLimit() {
    return getField7().get();
  }

  public UInt64 getGasUsed() {
    return getField8().get();
  }

  public UInt64 getTimestamp() {
    return getField9().get();
  }

  public Bytes getExtraData() {
    return getField10().getBytes();
  }

  public UInt256 getBaseFeePerGas() {
    return getField11().get();
  }

  public Bytes32 getBlockHash() {
    return getField12().get();
  }

  public SszList<Transaction> getTransactions() {
    return getField13();
  }
}
