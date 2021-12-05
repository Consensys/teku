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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszMutableComposite;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;

public interface SszCollectionSchema<
        SszElementT extends SszData, SszCollectionT extends SszCollection<SszElementT>>
    extends SszCompositeSchema<SszCollectionT> {

  SszSchema<SszElementT> getElementSchema();

  @Override
  default void storeChildNode(
      TreeNodeStore nodeStore, int maxBranchLevelsSkipped, long gIndex, TreeNode node) {
    getElementSchema().storeBackingNodes(nodeStore, maxBranchLevelsSkipped, gIndex, node);
  }

  @SuppressWarnings("unchecked")
  default SszCollectionT of(SszElementT... elements) {
    return createFromElements(Arrays.asList(elements));
  }

  @SuppressWarnings("unchecked")
  default SszCollectionT createFromElements(List<? extends SszElementT> elements) {
    checkArgument(elements.size() <= getMaxLength(), "Too many elements for this collection type");
    SszMutableComposite<SszElementT> writableCopy = getDefault().createWritableCopy();
    int idx = 0;
    for (SszElementT element : elements) {
      writableCopy.set(idx++, element);
    }
    return (SszCollectionT) writableCopy.commitChanges();
  }

  default TreeNode createTreeFromElements(List<SszElementT> elements) {
    // TODO: suboptimal
    return createFromElements(elements).getBackingNode();
  }

  default Collector<SszElementT, ?, SszCollectionT> collector() {
    return Collectors.collectingAndThen(Collectors.<SszElementT>toList(), this::createFromElements);
  }
}
