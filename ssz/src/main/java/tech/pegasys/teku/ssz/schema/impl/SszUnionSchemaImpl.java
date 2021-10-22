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

package tech.pegasys.teku.ssz.schema.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.nio.ByteOrder;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszUnion;
import tech.pegasys.teku.ssz.impl.SszUnionImpl;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.schema.SszType;
import tech.pegasys.teku.ssz.schema.SszUnionSchema;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;
import tech.pegasys.teku.ssz.tree.BranchNode;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
import tech.pegasys.teku.ssz.tree.LeafNode;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.ssz.tree.TreeUtil;

public abstract class SszUnionSchemaImpl<SszUnionT extends SszUnion>
    implements SszUnionSchema<SszUnionT> {

  private static final int MAX_SELECTOR = 127;
  private static final int DEFAULT_SELECTOR = 0;

  private static LeafNode createSelectorNode(int selector) {
    assert selector <= MAX_SELECTOR;
    return LeafNode.create(Bytes.of((byte) selector));
  }

  public static SszUnionSchema<SszUnion> createGenericSchema(List<SszSchema<?>> childrenSchemas) {
    return new SszUnionSchemaImpl<>(childrenSchemas) {
      @Override
      public SszUnion createFromBackingNode(TreeNode node) {
        return new SszUnionImpl(this, node);
      }
    };
  }

  private final List<SszSchema<?>> childrenSchemas;
  private final TreeNode defaultTree;
  private final Supplier<SszLengthBounds> lengthBounds =
      Suppliers.memoize(this::calcSszLengthBounds);

  public SszUnionSchemaImpl(List<SszSchema<?>> childrenSchemas) {
    checkArgument(childrenSchemas.size() < MAX_SELECTOR, "Too many child types");
    checkArgument(
        childrenSchemas.stream()
            .skip(1)
            .allMatch(schema -> schema != SszPrimitiveSchemas.NONE_SCHEMA),
        "None is allowed for zero selector only");
    this.childrenSchemas = childrenSchemas;
    defaultTree =
        createUnionNode(childrenSchemas.get(DEFAULT_SELECTOR).getDefaultTree(), DEFAULT_SELECTOR);
  }

  @Override
  public List<SszSchema<?>> getChildrenSchemas() {
    return childrenSchemas;
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public TreeNode getDefaultTree() {
    return defaultTree;
  }

  @Override
  @SuppressWarnings("unchecked")
  public abstract SszUnionT createFromBackingNode(TreeNode node);

  @Override
  public SszUnionT createFromValue(int selector, SszData value) {
    checkArgument(selector < getTypesCount(), "Selector is out of bounds");
    checkArgument(
        getChildSchema(selector).equals(value.getSchema()),
        "Incompatible value schema for supplied selector");
    return createFromBackingNode(createUnionNode(value.getBackingNode(), selector));
  }

  @Override
  public int getSszVariablePartSize(TreeNode node) {
    int selector = getSelector(node);
    return childrenSchemas.get(selector).getSszSize(getValueNode(node));
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    int selector = getSelector(node);
    writer.write(Bytes.of(selector));
    SszSchema<?> valueSchema = childrenSchemas.get(selector);
    int valueSszLength = valueSchema.sszSerializeTree(getValueNode(node), writer);
    return valueSszLength + SELECTOR_SIZE_BYTES;
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) throws SszDeserializeException {
    int selector = reader.read(1).get(0) & 0xFF;
    if (selector >= getTypesCount()) {
      throw new SszDeserializeException(
          "Invalid selector " + selector + " for Union schema: " + this);
    }
    SszSchema<?> valueSchema = childrenSchemas.get(selector);
    TreeNode valueNode = valueSchema.sszDeserializeTree(reader);

    return createUnionNode(valueNode, selector);
  }

  public int getSelectorFromSelectorNode(TreeNode selectorNode) {
    checkArgument(selectorNode instanceof LeafDataNode, "Invalid selector node");
    LeafDataNode dataNode = (LeafDataNode) selectorNode;
    Bytes bytes = dataNode.getData();
    checkArgument(bytes.size() == SELECTOR_SIZE_BYTES, "Invalid selector node");
    int selector = bytes.get(0) & 0xFF;
    checkArgument(selector <= MAX_SELECTOR, "Selector exceeds max value");
    return selector;
  }

  public TreeNode getValueNode(TreeNode unionNode) {
    return unionNode.get(GIndexUtil.LEFT_CHILD_G_INDEX);
  }

  private TreeNode getSelectorNode(TreeNode unionNode) {
    return unionNode.get(GIndexUtil.RIGHT_CHILD_G_INDEX);
  }

  public int getSelector(TreeNode unionNode) {
    int selector = getSelectorFromSelectorNode(getSelectorNode(unionNode));
    checkArgument(selector < getTypesCount(), "Selector is out of bounds");
    return selector;
  }

  private TreeNode createUnionNode(TreeNode valueNode, int selector) {
    return BranchNode.create(valueNode, createSelectorNode(selector));
  }

  @Override
  public void storeBackingNodes(
      TreeNodeStore nodeStore, int maxBranchLevelsSkipped, long rootGIndex, TreeNode node) {
    TreeNode selectorNode = getSelectorNode(node);
    TreeNode valueNode = getValueNode(node);
    int selector = getSelectorFromSelectorNode(selectorNode);
    nodeStore.storeLeafNode(selectorNode, GIndexUtil.gIdxRightGIndex(rootGIndex));

    getChildSchema(selector)
        .storeBackingNodes(
            nodeStore, maxBranchLevelsSkipped, GIndexUtil.gIdxLeftGIndex(rootGIndex), valueNode);

    nodeStore.storeBranchNode(
        node.hashTreeRoot(),
        rootGIndex,
        1,
        new Bytes32[] {valueNode.hashTreeRoot(), selectorNode.hashTreeRoot()});
  }

  @Override
  public TreeNode loadBackingNodes(TreeNodeSource nodeSource, Bytes32 rootHash, long rootGIndex) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootHash) || rootHash.equals(Bytes32.ZERO)) {
      return getDefaultTree();
    }

    final CompressedBranchInfo branchData = nodeSource.loadBranchNode(rootHash, rootGIndex);
    checkState(
        branchData.getChildren().length == 2, "Union root node must have exactly two children");
    checkState(branchData.getDepth() == 1, "Union root node must have depth of 1");
    final Bytes32 valueHash = branchData.getChildren()[0];
    final Bytes32 selectorHash = branchData.getChildren()[1];
    final int selector =
        nodeSource
            .loadLeafNode(selectorHash, GIndexUtil.gIdxRightGIndex(rootGIndex))
            .getInt(0, ByteOrder.LITTLE_ENDIAN);
    checkState(selector < getTypesCount(), "Selector is out of bounds");
    SszSchema<?> childSchema = getChildSchema(selector);
    TreeNode valueNode =
        childSchema.loadBackingNodes(nodeSource, valueHash, GIndexUtil.gIdxLeftGIndex(rootGIndex));
    return createUnionNode(valueNode, selector);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return lengthBounds.get();
  }

  private SszLengthBounds calcSszLengthBounds() {
    return IntStream.range(0, getTypesCount())
        .mapToObj(this::getChildSchema)
        .map(SszType::getSszLengthBounds)
        .reduce(SszLengthBounds::or)
        .orElseThrow()
        .addBytes(SELECTOR_SIZE_BYTES)
        .ceilToBytes();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszUnionSchemaImpl)) {
      return false;
    }
    SszUnionSchemaImpl<?> that = (SszUnionSchemaImpl<?>) o;
    return Objects.equal(childrenSchemas, that.childrenSchemas);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(childrenSchemas);
  }

  @Override
  public String toString() {
    return "Union" + childrenSchemas;
  }
}
