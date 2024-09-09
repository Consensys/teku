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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Profile20;
import tech.pegasys.teku.infrastructure.ssz.containers.ProfileSchema20;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ExecutionPayloadHeaderElectraImpl
    extends Profile20<
        ExecutionPayloadHeaderElectraImpl,
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
        SszBytes32,
        SszBytes32,
        SszUInt64,
        SszUInt64,
        SszBytes32,
        SszBytes32,
        SszBytes32>
    implements ExecutionPayloadHeaderElectra {

  protected ExecutionPayloadHeaderElectraImpl(
      final ProfileSchema20<
              ExecutionPayloadHeaderElectraImpl,
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
              SszBytes32,
              SszBytes32,
              SszUInt64,
              SszUInt64,
              SszBytes32,
              SszBytes32,
              SszBytes32>
          schema,
      final TreeNode backingTree) {
    super(schema, backingTree);
  }

  public ExecutionPayloadHeaderElectraImpl(
      final ExecutionPayloadHeaderSchemaElectra schema,
      final SszBytes32 parentHash,
      final SszByteVector feeRecipient,
      final SszBytes32 stateRoot,
      final SszBytes32 receiptsRoot,
      final SszByteVector logsBloom,
      final SszBytes32 prevRandao,
      final SszUInt64 blockNumber,
      final SszUInt64 gasLimit,
      final SszUInt64 gasUsed,
      final SszUInt64 timestamp,
      final SszByteList extraData,
      final SszUInt256 baseFeePerGas,
      final SszBytes32 blockHash,
      final SszBytes32 transactionsRoot,
      final SszBytes32 withdrawalsRoot,
      final SszUInt64 blobGasUsed,
      final SszUInt64 excessBlobGas,
      final SszBytes32 depositRequestsRoot,
      final SszBytes32 withdrawalRequestsRoot,
      final SszBytes32 consolidationRequestsRoot) {
    super(
        schema,
        parentHash,
        feeRecipient,
        stateRoot,
        receiptsRoot,
        logsBloom,
        prevRandao,
        blockNumber,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFeePerGas,
        blockHash,
        transactionsRoot,
        withdrawalsRoot,
        blobGasUsed,
        excessBlobGas,
        depositRequestsRoot,
        withdrawalRequestsRoot,
        consolidationRequestsRoot);
  }

  @Override
  public boolean isDefaultPayload() {
    return isHeaderOfDefaultPayload();
  }

  @Override
  public ExecutionPayloadHeaderSchemaElectra getSchema() {
    return (ExecutionPayloadHeaderSchemaElectra) super.getSchema();
  }

  @Override
  public boolean isHeaderOfDefaultPayload() {
    return equals(getSchema().getHeaderOfDefaultPayload());
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
  public Bytes32 getBlockHash() {
    return getField12().get();
  }

  @Override
  public Bytes32 getTransactionsRoot() {
    return getField13().get();
  }

  @Override
  public Bytes32 getPayloadHash() {
    return hashTreeRoot();
  }

  @Override
  public Bytes32 getWithdrawalsRoot() {
    return getField14().get();
  }

  @Override
  public UInt64 getBlobGasUsed() {
    return getField15().get();
  }

  @Override
  public UInt64 getExcessBlobGas() {
    return getField16().get();
  }

  @Override
  public Bytes32 getDepositRequestsRoot() {
    return getField17().get();
  }

  @Override
  public Bytes32 getWithdrawalRequestsRoot() {
    return getField18().get();
  }

  @Override
  public Bytes32 getConsolidationRequestsRoot() {
    return getField19().get();
  }
}
