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

package tech.pegasys.teku.ssz.backing.schema;

import java.util.List;
import tech.pegasys.teku.ssz.backing.SszCollection;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszMutableComposite;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public interface SszCollectionSchema<
        SszElementT extends SszData, SszCollectionT extends SszCollection<SszElementT>>
    extends SszCompositeSchema<SszCollectionT> {

  SszSchema<SszElementT> getElementSchema();

  @SuppressWarnings("unchecked")
  default SszCollectionT createFromElements(List<SszElementT> elements) {
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
}
