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

package tech.pegasys.teku.ssz.schema.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.ssz.tree.TreeUtil.bitsCeilToBytes;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszPrimitive;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
import tech.pegasys.teku.ssz.tree.LeafNode;
import tech.pegasys.teku.ssz.tree.TreeNode;

/**
 * Represents primitive view type
 *
 * @param <SszDataT> Class of the basic view of this type
 */
public abstract class AbstractSszPrimitiveSchema<
        DataT, SszDataT extends SszPrimitive<DataT, SszDataT>>
    implements SszPrimitiveSchema<DataT, SszDataT> {

  private final int bitsSize;
  private final int sszSize;

  protected AbstractSszPrimitiveSchema(int bitsSize) {
    checkArgument(
        bitsSize > 0 && bitsSize <= 256 && 256 % bitsSize == 0, "Invalid bitsize: %s", bitsSize);
    this.bitsSize = bitsSize;
    this.sszSize = getSSZBytesSize();
  }

  @Override
  public int getBitsSize() {
    return bitsSize;
  }

  @Override
  public SszDataT createFromBackingNode(TreeNode node) {
    return createFromPackedNode(node, 0);
  }

  @Override
  public final SszDataT createFromPackedNode(TreeNode node, int internalIndex) {
    assert node instanceof LeafDataNode;
    return createFromLeafBackingNode((LeafDataNode) node, internalIndex);
  }

  public abstract SszDataT createFromLeafBackingNode(LeafDataNode node, int internalIndex);

  public TreeNode createBackingNode(SszDataT newValue) {
    return updateBackingNode(LeafNode.EMPTY_LEAF, 0, newValue);
  }

  @Override
  public TreeNode updatePackedNode(TreeNode srcNode, List<PackedNodeUpdate<DataT, SszDataT>> updates) {
    TreeNode res = srcNode;
    for (PackedNodeUpdate<DataT, SszDataT> update : updates) {
      res = updateBackingNode(res, update.getInternalIndex(), update.getNewValue());
    }
    return res;
  }

  protected abstract TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, SszData newValue);

  private int getSSZBytesSize() {
    return bitsCeilToBytes(getBitsSize());
  }

  @Override
  public boolean isFixedSize() {
    return true;
  }

  @Override
  public int getSszFixedPartSize() {
    return sszSize;
  }

  @Override
  public int getSszVariablePartSize(TreeNode node) {
    return 0;
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    int sszBytesSize = getSSZBytesSize();
    final Bytes nodeData;
    if (node instanceof LeafDataNode) {
      // small perf optimization
      nodeData = ((LeafDataNode) node).getData();
    } else {
      nodeData = node.hashTreeRoot();
    }
    writer.write(nodeData.toArrayUnsafe(), 0, sszBytesSize);
    return sszBytesSize;
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    Bytes bytes = reader.read(getSSZBytesSize());
    if (reader.getAvailableBytes() > 0) {
      throw new SszDeserializeException("Extra " + reader.getAvailableBytes() + " bytes found");
    }
    return LeafNode.create(bytes);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return SszLengthBounds.ofBits(getBitsSize());
  }
}
