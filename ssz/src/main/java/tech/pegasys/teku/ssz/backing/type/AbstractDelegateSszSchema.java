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

package tech.pegasys.teku.ssz.backing.type;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;

/**
 * Helper `SszSchema` for making custom parametrized schemas without complexity of overriding
 * existing schemas
 */
public abstract class AbstractDelegateSszSchema<SszDataT extends SszData>
    implements SszSchema<SszDataT> {

  private final SszSchema<? super SszDataT> delegate;

  protected AbstractDelegateSszSchema(SszSchema<? super SszDataT> delegate) {
    this.delegate = delegate;
  }

  @Override
  public abstract SszDataT createFromBackingNode(TreeNode node);

  @Override
  public TreeNode getDefaultTree() {
    return delegate.getDefaultTree();
  }

  @Override
  public SszDataT getDefault() {
    return createFromBackingNode(delegate.getDefaultTree());
  }

  @Override
  public int getBitsSize() {
    return delegate.getBitsSize();
  }

  @Override
  public SszDataT createFromBackingNode(TreeNode node, int internalIndex) {
    return createFromBackingNode(node);
  }

  @Override
  public TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, SszData newValue) {
    return delegate.updateBackingNode(srcNode, internalIndex, newValue);
  }

  @Override
  public Bytes sszSerialize(SszDataT view) {
    return delegate.sszSerialize(view);
  }

  @Override
  public int sszSerialize(SszDataT view, SszWriter writer) {
    return delegate.sszSerialize(view, writer);
  }

  @Override
  public SszDataT sszDeserialize(SszReader reader) throws SszDeserializeException {
    return createFromBackingNode(delegate.sszDeserializeTree(reader));
  }

  @Override
  public SszDataT sszDeserialize(Bytes ssz) throws SszDeserializeException {
    return sszDeserialize(SszReader.fromBytes(ssz));
  }

  public static Bytes lengthToBytes(int length) {
    return SszType.lengthToBytes(length);
  }

  public static int bytesToLength(Bytes bytes) {
    return SszType.bytesToLength(bytes);
  }

  @Override
  public boolean isFixedSize() {
    return delegate.isFixedSize();
  }

  @Override
  public int getFixedPartSize() {
    return delegate.getFixedPartSize();
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    return delegate.getVariablePartSize(node);
  }

  @Override
  public int getSszSize(TreeNode node) {
    return delegate.getSszSize(node);
  }

  @Override
  public Bytes sszSerializeTree(TreeNode node) {
    return delegate.sszSerializeTree(node);
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    return delegate.sszSerializeTree(node, writer);
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) throws SszDeserializeException {
    return delegate.sszDeserializeTree(reader);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return delegate.getSszLengthBounds();
  }
}
