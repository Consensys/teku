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

package tech.pegasys.teku.spec.datastructures.execution.versions.gloas;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container9;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ExecutionPayloadHeaderGloasImpl
    extends Container9<
        ExecutionPayloadHeaderGloasImpl,
        SszBytes32,
        SszBytes32,
        SszBytes32,
        SszByteVector,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszBytes32>
    implements ExecutionPayloadHeaderGloas {

  protected ExecutionPayloadHeaderGloasImpl(
      final ExecutionPayloadHeaderSchemaGloas schema, final TreeNode backingTree) {
    super(schema, backingTree);
  }

  public ExecutionPayloadHeaderGloasImpl(
      final ExecutionPayloadHeaderSchemaGloas schema,
      final SszBytes32 parentBlockHash,
      final SszBytes32 parentBlockRoot,
      final SszBytes32 blockHash,
      final SszByteVector feeRecipient,
      final SszUInt64 gasLimit,
      final SszUInt64 builderIndex,
      final SszUInt64 slot,
      final SszUInt64 value,
      final SszBytes32 blobKzgCommitmentsRoot) {
    super(
        schema,
        parentBlockHash,
        parentBlockRoot,
        blockHash,
        feeRecipient,
        gasLimit,
        builderIndex,
        slot,
        value,
        blobKzgCommitmentsRoot);
  }

  @Override
  public Bytes32 getParentBlockHash() {
    return getField0().get();
  }

  @Override
  public Bytes32 getParentBlockRoot() {
    return getField1().get();
  }

  @Override
  public Bytes32 getBlockHash() {
    return getField2().get();
  }

  @Override
  public Bytes20 getFeeRecipient() {
    return new Bytes20(getField3().getBytes());
  }

  @Override
  public UInt64 getGasLimit() {
    return getField4().get();
  }

  @Override
  public UInt64 getBuilderIndex() {
    return getField5().get();
  }

  @Override
  public UInt64 getSlot() {
    return getField6().get();
  }

  @Override
  public UInt64 getValue() {
    return getField7().get();
  }

  @Override
  public Bytes32 getBlobKzgCommitmentsRoot() {
    return getField8().get();
  }

  @Override
  public boolean isDefaultPayload() {
    return isHeaderOfDefaultPayload();
  }

  @Override
  public ExecutionPayloadHeaderSchemaGloas getSchema() {
    return (ExecutionPayloadHeaderSchemaGloas) super.getSchema();
  }

  @Override
  public boolean isHeaderOfDefaultPayload() {
    return equals(getSchema().getHeaderOfDefaultPayload());
  }
}
