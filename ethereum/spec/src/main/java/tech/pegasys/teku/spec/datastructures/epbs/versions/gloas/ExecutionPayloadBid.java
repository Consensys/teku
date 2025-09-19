/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container9;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ExecutionPayloadBid
    extends Container9<
        ExecutionPayloadBid,
        SszBytes32,
        SszBytes32,
        SszBytes32,
        SszByteVector,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszBytes32> {

  protected ExecutionPayloadBid(
      final ExecutionPayloadBidSchema schema,
      final Bytes32 parentBlockHash,
      final Bytes32 parentBlockRoot,
      final Bytes32 blockHash,
      final Bytes20 feeRecipient,
      final UInt64 gasLimit,
      final UInt64 builderIndex,
      final UInt64 slot,
      final UInt64 value,
      final Bytes32 blobKzgCommitmentsRoot) {
    super(
        schema,
        SszBytes32.of(parentBlockHash),
        SszBytes32.of(parentBlockRoot),
        SszBytes32.of(blockHash),
        SszByteVector.fromBytes(feeRecipient.getWrappedBytes()),
        SszUInt64.of(gasLimit),
        SszUInt64.of(builderIndex),
        SszUInt64.of(slot),
        SszUInt64.of(value),
        SszBytes32.of(blobKzgCommitmentsRoot));
  }

  protected ExecutionPayloadBid(
      final ExecutionPayloadBidSchema schema, final TreeNode backingTree) {
    super(schema, backingTree);
  }

  public Bytes32 getParentBlockHash() {
    return getField0().get();
  }

  public Bytes32 getParentBlockRoot() {
    return getField1().get();
  }

  public Bytes32 getBlockHash() {
    return getField2().get();
  }

  public Eth1Address getFeeRecipient() {
    return Eth1Address.fromBytes(getField3().getBytes());
  }

  public UInt64 getGasLimit() {
    return getField4().get();
  }

  public UInt64 getBuilderIndex() {
    return getField5().get();
  }

  public UInt64 getSlot() {
    return getField6().get();
  }

  public UInt64 getValue() {
    return getField7().get();
  }

  public Bytes32 getBlobKzgCommitmentsRoot() {
    return getField8().get();
  }

  @Override
  public ExecutionPayloadBidSchema getSchema() {
    return (ExecutionPayloadBidSchema) super.getSchema();
  }
}
