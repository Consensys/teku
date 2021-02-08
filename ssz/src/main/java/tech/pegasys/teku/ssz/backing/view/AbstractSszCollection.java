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

package tech.pegasys.teku.ssz.backing.view;

import tech.pegasys.teku.ssz.backing.SszCollection;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.schema.SszCollectionSchema;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public abstract class AbstractSszCollection<SszElementT extends SszData>
    extends AbstractSszComposite<SszElementT> implements SszCollection<SszElementT> {

  protected AbstractSszCollection(SszCollection<SszElementT> otherComposite) {
    super(otherComposite);
  }

  protected AbstractSszCollection(SszCompositeSchema<?> schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected AbstractSszCollection(
      SszCompositeSchema<?> schema, TreeNode backingNode, IntCache<SszElementT> cache) {
    super(schema, backingNode, cache);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected SszElementT getImpl(int index) {
    SszCollectionSchema<SszElementT, ?> type =
        (SszCollectionSchema<SszElementT, ?>) this.getSchema();
    SszSchema<?> elementType = type.getElementSchema();
    TreeNode node =
        getBackingNode().get(type.getGeneralizedIndex(index / type.getElementsPerChunk()));
    return (SszElementT)
        elementType.createFromBackingNode(node, index % type.getElementsPerChunk());
  }
}
