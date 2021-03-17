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

package tech.pegasys.teku.ssz.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.ssz.InvalidValueSchemaException;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.tree.LeafNode;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeUpdates;

public abstract class AbstractSszMutableCollection<
        SszElementT extends SszData, SszMutableElementT extends SszElementT>
    extends AbstractSszMutableComposite<SszElementT, SszMutableElementT> {

  private final SszSchema<SszElementT> elementSchema;

  protected AbstractSszMutableCollection(AbstractSszComposite<SszElementT> backingImmutableData) {
    super(backingImmutableData);
    elementSchema = getSchema().getElementSchema();
  }

  private SszSchema<SszElementT> getElementSchema() {
    return elementSchema;
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszCollectionSchema<SszElementT, ?> getSchema() {
    return (SszCollectionSchema<SszElementT, ?>) super.getSchema();
  }

  @Override
  public void set(int index, SszElementT value) {
    if (!value.getSchema().equals(getElementSchema())) {
      throw new InvalidValueSchemaException(
          "Expected element to have schema "
              + getSchema().getChildSchema(index)
              + ", but value has schema "
              + value.getSchema());
    }
    setUnsafe(index, value);
  }

  @Override
  protected TreeUpdates packChanges(
      Stream<Map.Entry<Integer, SszElementT>> newChildValues, TreeNode original) {
    SszCollectionSchema<?, ?> type = getSchema();
    SszSchema<?> elementType = type.getElementSchema();
    int elementsPerChunk = type.getElementsPerChunk();

    List<Map.Entry<Integer, SszElementT>> newChildren = newChildValues.collect(Collectors.toList());
    int prevChildNodeIndex = 0;
    List<NodeUpdate> nodeUpdates = new ArrayList<>();
    NodeUpdate curNodeUpdate = null;

    for (Map.Entry<Integer, SszElementT> entry : newChildren) {
      int childIndex = entry.getKey();
      int childNodeIndex = childIndex / elementsPerChunk;

      if (curNodeUpdate == null || childNodeIndex != prevChildNodeIndex) {
        long gIndex = type.getChildGeneralizedIndex(childNodeIndex);
        curNodeUpdate = new NodeUpdate(gIndex, elementsPerChunk);
        nodeUpdates.add(curNodeUpdate);
        prevChildNodeIndex = childNodeIndex;
      }
      curNodeUpdate.addUpdate(childIndex % elementsPerChunk, entry.getValue());
    }

    List<Long> gIndexes = new ArrayList<>();
    List<TreeNode> newValues = new ArrayList<>();
    for (NodeUpdate nodeUpdate : nodeUpdates) {
      long gIndex = nodeUpdate.getNodeGIndex();
      TreeNode originalNode =
          nodeUpdate.getNodeUpdatesCount() < elementsPerChunk
              ? original.get(gIndex)
              : LeafNode.EMPTY_LEAF;
      TreeNode newNode =
          elementType.updateBackingNode(
              originalNode,
              nodeUpdate.getInternalIndexes(),
              nodeUpdate.getNewValues(),
              nodeUpdate.getNodeUpdatesCount());
      newValues.add(newNode);
      gIndexes.add(gIndex);
    }

    return new TreeUpdates(gIndexes, newValues);
  }

  private static class NodeUpdate {
    private final int[] internalIndexes;
    private final SszData[] newValues;
    private final long nodeGIndex;
    private int nodeUpdatesCount = 0;

    public NodeUpdate(long nodeGIndex, int maxElementsPerChunk) {
      internalIndexes = new int[maxElementsPerChunk];
      this.nodeGIndex = nodeGIndex;
      this.newValues = new SszData[maxElementsPerChunk];
    }

    public void addUpdate(int internalNodeIndex, SszData newValue) {
      internalIndexes[nodeUpdatesCount] = internalNodeIndex;
      newValues[nodeUpdatesCount] = newValue;
      nodeUpdatesCount++;
    }

    public int[] getInternalIndexes() {
      return internalIndexes;
    }

    public SszData[] getNewValues() {
      return newValues;
    }

    public int getNodeUpdatesCount() {
      return nodeUpdatesCount;
    }

    public long getNodeGIndex() {
      return nodeGIndex;
    }
  }
}
