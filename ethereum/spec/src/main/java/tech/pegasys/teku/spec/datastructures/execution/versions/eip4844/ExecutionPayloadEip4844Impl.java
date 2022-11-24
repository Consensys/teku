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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container16;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema16;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;

public class ExecutionPayloadEip4844Impl
    extends Container16<
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
        SszUInt64,
        SszBytes32,
        SszList<Transaction>,
        SszList<Withdrawal>>
    implements ExecutionPayloadEip4844 {

  public ExecutionPayloadEip4844Impl(
      ContainerSchema16<
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
              SszUInt64,
              SszBytes32,
              SszList<Transaction>,
              SszList<Withdrawal>>
          schema,
      TreeNode backingNode) {
    super(schema, backingNode);
  }

  public ExecutionPayloadEip4844Impl(
      ExecutionPayloadSchemaEip4844 schema,
      SszBytes32 parentHash,
      SszByteVector feeRecipient,
      SszBytes32 stateRoot,
      SszBytes32 receiptsRoot,
      SszByteVector logsBloom,
      SszBytes32 random,
      SszUInt64 blockNumber,
      SszUInt64 gasLimit,
      SszUInt64 gasUsed,
      SszUInt64 timestamp,
      SszByteList extraData,
      SszUInt256 baseFeePerGas,
      SszUInt64 excessBlobs,
      SszBytes32 blockHash,
      SszList<Transaction> transactions,
      SszList<Withdrawal> withdrawals) {
    super(
        schema,
        parentHash,
        feeRecipient,
        stateRoot,
        receiptsRoot,
        logsBloom,
        random,
        blockNumber,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFeePerGas,
        excessBlobs,
        blockHash,
        transactions,
        withdrawals);
  }

  @Override
  public boolean isDefaultPayload() {
    return super.isDefault();
  }

  @Override
  public ExecutionPayloadSchemaEip4844 getSchema() {
    return (ExecutionPayloadSchemaEip4844) super.getSchema();
  }

  @Override
  public Bytes32 getParentHash() {
    return getField0().get();
  }

  @Override
  public Bytes20 getFeeRecipient() {
    return Bytes20.leftPad(getField1().getBytes());
  }

  @Override
  public Bytes32 getStateRoot() {
    return getField2().get();
  }

  @Override
  public Bytes32 getReceiptsRoot() {
    return getField3().get();
  }

  @Override
  public Bytes getLogsBloom() {
    return getField4().getBytes();
  }

  @Override
  public Bytes32 getPrevRandao() {
    return getField5().get();
  }

  @Override
  public UInt64 getBlockNumber() {
    return getField6().get();
  }

  @Override
  public UInt64 getGasLimit() {
    return getField7().get();
  }

  @Override
  public UInt64 getGasUsed() {
    return getField8().get();
  }

  @Override
  public UInt64 getTimestamp() {
    return getField9().get();
  }

  @Override
  public Bytes getExtraData() {
    return getField10().getBytes();
  }

  @Override
  public UInt256 getBaseFeePerGas() {
    return getField11().get();
  }

  @Override
  public UInt64 getExcessBlobs() {
    return getField12().get();
  }

  @Override
  public Bytes32 getBlockHash() {
    return getField13().get();
  }

  @Override
  public Bytes32 getPayloadHash() {
    return hashTreeRoot();
  }

  @Override
  public SszList<Transaction> getTransactions() {
    return getField14();
  }

  @Override
  public SszList<Withdrawal> getWithdrawals() {
    return getField15();
  }

  @Override
  public List<TreeNode> getUnblindedTreeNodes() {
    return List.of(getTransactions().getBackingNode(), getWithdrawals().getBackingNode());
  }
}
