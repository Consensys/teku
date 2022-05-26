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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszPrimitiveListImpl<ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszListImpl<SszElementT> implements SszPrimitiveList<ElementT, SszElementT> {

  private final static CachedTreeNode NA_CACHED_NODE = new CachedTreeNode(-1, null);

  private volatile CachedTreeNode cachedTreeNode = NA_CACHED_NODE;

  private final SszListSchema<SszElementT, ?> schema;
  private final int elementsPerChunk;
  private final SszPrimitiveSchema<ElementT, SszElementT> elementType;

  public SszPrimitiveListImpl(SszListSchema<SszElementT, ?> schema, TreeNode backingNode) {
    super(schema, backingNode);
    this.schema = schema;
    this.elementsPerChunk = schema.getElementsPerChunk();
    this.elementType = (SszPrimitiveSchema<ElementT, SszElementT>) schema.getElementSchema();
  }

  public SszPrimitiveListImpl(
      SszListSchema<SszElementT, ?> schema, TreeNode backingNode, IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
    this.schema = schema;
    this.elementsPerChunk = schema.getElementsPerChunk();
    this.elementType = (SszPrimitiveSchema<ElementT, SszElementT>) schema.getElementSchema();
  }

  @Override
  public ElementT getElement(int index) {
    return elementType.createFromPackedNodeUnboxed(getTreeNode(index), index % elementsPerChunk);
  }

  @Override
  protected SszElementT getImpl(int index) {
    return elementType.createFromPackedNode(getTreeNode(index), index % elementsPerChunk);
  }

  private TreeNode getTreeNode(int index) {
    int nodeIndex = index / elementsPerChunk;
    CachedTreeNode cached = cachedTreeNode;
    if (cached.getNodeIndex() == nodeIndex) {
      return cached.getNode();
    } else {
      TreeNode node = getBackingNode().get(schema.getChildGeneralizedIndex(nodeIndex));
      cachedTreeNode = new CachedTreeNode(nodeIndex, node);
      return node;
    }
  }

  @Override
  public SszMutablePrimitiveList<ElementT, SszElementT> createWritableCopy() {
    return new SszMutablePrimitiveListImpl<>(this);
  }

  private static class CachedTreeNode {

    private final int nodeIndex;
    private final TreeNode node;

    public CachedTreeNode(int nodeIndex, TreeNode node) {
      this.nodeIndex = nodeIndex;
      this.node = node;
    }

    public int getNodeIndex() {
      return nodeIndex;
    }

    public TreeNode getNode() {
      return node;
    }
  }
}
