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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.collections.SszByteVector;
import tech.pegasys.teku.ssz.containers.Container13;
import tech.pegasys.teku.ssz.containers.ContainerSchema13;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.type.Bytes20;

public class ExecutionPayload
    extends Container13<
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
        SszBytes32,
        SszBytes32,
        SszList<Transaction>> {

  public static class ExecutionPayloadSchema
      extends ContainerSchema13<
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
          SszBytes32,
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
          namedSchema("base_fee_per_gas", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema(
              "transactions",
              SszListSchema.create(Transaction.SSZ_SCHEMA, SpecConfig.MAX_EXECUTION_TRANSACTIONS)));
    }

    @SuppressWarnings("unchecked")
    public SszListSchema<Transaction, ?> getTransactionsSchema() {
      return (SszListSchema<Transaction, ?>) getFieldSchema12();
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
      ContainerSchema13<
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
              SszBytes32,
              SszBytes32,
              SszList<Transaction>>
          type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public ExecutionPayload(
      Bytes32 parent_hash,
      Bytes20 coinbase,
      Bytes32 state_root,
      Bytes32 receipt_root,
      Bytes logs_bloom,
      Bytes32 random,
      UInt64 blockNumber,
      UInt64 gas_limit,
      UInt64 gas_used,
      UInt64 timestamp,
      Bytes32 baseFeePerGas,
      Bytes32 block_hash,
      List<Bytes> transactions) {
    super(
        SSZ_SCHEMA,
        SszBytes32.of(parent_hash),
        SszByteVector.fromBytes(coinbase.getWrappedBytes()),
        SszBytes32.of(state_root),
        SszBytes32.of(receipt_root),
        SszByteVector.fromBytes(logs_bloom),
        SszBytes32.of(random),
        SszUInt64.of(blockNumber),
        SszUInt64.of(gas_limit),
        SszUInt64.of(gas_used),
        SszUInt64.of(timestamp),
        SszBytes32.of(baseFeePerGas),
        SszBytes32.of(block_hash),
        transactions.stream()
            .map(txBytes -> Transaction.SSZ_SCHEMA.getOpaqueTransactionSchema().fromBytes(txBytes))
            .map(Transaction.SSZ_SCHEMA::createOpaque)
            .collect(SSZ_SCHEMA.getTransactionsSchema().collector()));
  }

  @Override
  public ExecutionPayloadSchema getSchema() {
    return SSZ_SCHEMA;
  }

  public Bytes32 getParent_hash() {
    return getField0().get();
  }

  public Bytes20 getCoinbase() {
    return Bytes20.leftPad(getField1().getBytes());
  }

  public Bytes32 getState_root() {
    return getField2().get();
  }

  public Bytes32 getReceipt_root() {
    return getField3().get();
  }

  public Bytes getLogs_bloom() {
    return getField4().getBytes();
  }

  public Bytes32 getRandom() {
    return getField5().get();
  }

  public UInt64 getBlockNumber() {
    return getField6().get();
  }

  public UInt64 getGas_limit() {
    return getField7().get();
  }

  public UInt64 getGas_used() {
    return getField8().get();
  }

  public UInt64 getTimestamp() {
    return getField9().get();
  }

  public Bytes32 getBaseFeePerGas() {
    return getField10().get();
  }

  public Bytes32 getBlock_hash() {
    return getField11().get();
  }

  public SszList<Transaction> getTransactions() {
    return getField12();
  }
}
