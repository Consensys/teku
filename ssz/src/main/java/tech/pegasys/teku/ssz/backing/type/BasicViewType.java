/*
 * Copyright 2020 ConsenSys AG.
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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SszReader;

/**
 * Represents primitive view type
 *
 * @param <C> Class of the basic view of this type
 */
public abstract class BasicViewType<C extends ViewRead> implements ViewType<C> {

  private final int bitsSize;

  BasicViewType(int bitsSize) {
    checkArgument(
        bitsSize > 0 && bitsSize <= 256 && 256 % bitsSize == 0, "Invalid bitsize: %s", bitsSize);
    this.bitsSize = bitsSize;
  }

  @Override
  public int getBitsSize() {
    return bitsSize;
  }

  @Override
  public C createFromBackingNode(TreeNode node) {
    return createFromBackingNode(node, 0);
  }

  @Override
  public abstract C createFromBackingNode(TreeNode node, int internalIndex);

  public TreeNode createBackingNode(C newValue) {
    return updateBackingNode(LeafNode.EMPTY_LEAF, 0, newValue);
  }

  @Override
  public abstract TreeNode updateBackingNode(
      TreeNode srcNode, int internalIndex, ViewRead newValue);

  private int getSSZBytesSize() {
    return (getBitsSize() + 7) / 8;
  }

  @Override
  public boolean isFixedSize() {
    return true;
  }

  @Override
  public int getFixedPartSize() {
    return getSSZBytesSize();
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    return 0;
  }

  @Override
  public int sszSerialize(TreeNode node, Consumer<Bytes> writer) {
    Bytes ret = node.hashTreeRoot().slice(0, getSSZBytesSize());
    writer.accept(ret);
    return ret.size();
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    Bytes bytes = reader.read(getSSZBytesSize());
    return LeafNode.create(bytes);
  }
}
