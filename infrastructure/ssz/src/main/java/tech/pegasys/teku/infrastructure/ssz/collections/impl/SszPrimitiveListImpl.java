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
import tech.pegasys.teku.infrastructure.ssz.tree.CachingTreeAccessor;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszPrimitiveListImpl<ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszListImpl<SszElementT> implements SszPrimitiveList<ElementT, SszElementT> {

  private final int elementsPerChunk;
  private final SszPrimitiveSchema<ElementT, SszElementT> elementType;
  private final CachingTreeAccessor cachingTreeAccessor;

  public SszPrimitiveListImpl(SszListSchema<SszElementT, ?> schema, TreeNode backingNode) {
    super(schema, backingNode);
    this.elementsPerChunk = schema.getElementsPerChunk();
    this.elementType = (SszPrimitiveSchema<ElementT, SszElementT>) schema.getElementSchema();
    this.cachingTreeAccessor =
        new CachingTreeAccessor(backingNode, schema::getChildGeneralizedIndex);
  }

  public SszPrimitiveListImpl(
      SszListSchema<SszElementT, ?> schema, TreeNode backingNode, IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
    this.elementsPerChunk = schema.getElementsPerChunk();
    this.elementType = (SszPrimitiveSchema<ElementT, SszElementT>) schema.getElementSchema();
    this.cachingTreeAccessor =
        new CachingTreeAccessor(backingNode, schema::getChildGeneralizedIndex);
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
    return cachingTreeAccessor.getNodeByVectorIndex(nodeIndex);
  }

  @Override
  public SszMutablePrimitiveList<ElementT, SszElementT> createWritableCopy() {
    return new SszMutablePrimitiveListImpl<>(this);
  }
}
