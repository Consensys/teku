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
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.collections.SszByteVector;
import tech.pegasys.teku.ssz.containers.Container11;
import tech.pegasys.teku.ssz.containers.ContainerSchema11;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.type.Bytes20;

public class ExecutionPayload
    extends Container11<
        ExecutionPayload,
        SszBytes32,
        SszBytes32,
        SszByteVector,
        SszBytes32,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszBytes32,
        SszByteVector,
        SszList<SszByteList>> {

  public static class ExecutionPayloadSchema
      extends ContainerSchema11<
          ExecutionPayload,
          SszBytes32,
          SszBytes32,
          SszByteVector,
          SszBytes32,
          SszUInt64,
          SszUInt64,
          SszUInt64,
          SszUInt64,
          SszBytes32,
          SszByteVector,
          SszList<SszByteList>> {

    public ExecutionPayloadSchema() {
      super(
          "ExecutionPayload",
          namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("parent_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("coinbase", SszByteVectorSchema.create(Bytes20.SIZE)),
          namedSchema("state_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("number", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("gas_limit", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("gas_used", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("timestamp", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("receipt_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("logs_bloom", SszByteVectorSchema.create(SpecConfig.BYTES_PER_LOGS_BLOOM)),
          namedSchema(
              "transactions",
              SszListSchema.create(
                  SszByteListSchema.create(SpecConfig.MAX_BYTES_PER_OPAQUE_TRANSACTION),
                  SpecConfig.MAX_EXECUTION_TRANSACTIONS)));
    }

    @SuppressWarnings("unchecked")
    public SszListSchema<SszByteList, ?> getTransactionsSchema() {
      return (SszListSchema<SszByteList, ?>) getFieldSchema10();
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
      ContainerSchema11<
              ExecutionPayload,
              SszBytes32,
              SszBytes32,
              SszByteVector,
              SszBytes32,
              SszUInt64,
              SszUInt64,
              SszUInt64,
              SszUInt64,
              SszBytes32,
              SszByteVector,
              SszList<SszByteList>>
          type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public ExecutionPayload(
      Bytes32 block_hash,
      Bytes32 parent_hash,
      Bytes20 coinbase,
      Bytes32 state_root,
      UInt64 number,
      UInt64 gas_limit,
      UInt64 gas_used,
      UInt64 timestamp,
      Bytes32 receipt_root,
      Bytes logs_bloom,
      List<Bytes> transactions) {
    super(
        SSZ_SCHEMA,
        SszBytes32.of(block_hash),
        SszBytes32.of(parent_hash),
        SszByteVector.fromBytes(coinbase.getWrappedBytes()),
        SszBytes32.of(state_root),
        SszUInt64.of(number),
        SszUInt64.of(gas_limit),
        SszUInt64.of(gas_used),
        SszUInt64.of(timestamp),
        SszBytes32.of(receipt_root),
        SszByteVector.fromBytes(logs_bloom),
        transactions.stream()
            .map(
                tx ->
                    ((SszByteListSchema<?>) SSZ_SCHEMA.getTransactionsSchema().getElementSchema())
                        .fromBytes(tx))
            .collect(SSZ_SCHEMA.getTransactionsSchema().collector()));
  }

  @Override
  public ExecutionPayloadSchema getSchema() {
    return SSZ_SCHEMA;
  }

  public Bytes32 getBlock_hash() {
    return getField0().get();
  }

  public Bytes32 getParent_hash() {
    return getField1().get();
  }

  public Bytes20 getCoinbase() {
    return Bytes20.leftPad(getField2().getBytes());
  }

  public Bytes32 getState_root() {
    return getField3().get();
  }

  public UInt64 getNumber() {
    return getField4().get();
  }

  public UInt64 getGas_limit() {
    return getField5().get();
  }

  public UInt64 getGas_used() {
    return getField6().get();
  }

  public UInt64 getTimestamp() {
    return getField7().get();
  }

  public Bytes32 getReceipt_root() {
    return getField8().get();
  }

  public Bytes getLogs_bloom() {
    return getField9().getBytes();
  }

  public SszList<SszByteList> getTransactions() {
    return getField10();
  }
}
