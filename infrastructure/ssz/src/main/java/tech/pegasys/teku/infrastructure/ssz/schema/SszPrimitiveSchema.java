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

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public interface SszPrimitiveSchema<DataT, SszDataT extends SszPrimitive<DataT, SszDataT>>
    extends SszSchema<SszDataT> {

  int getBitsSize();

  SszDataT boxed(DataT rawValue);

  @Override
  default boolean isPrimitive() {
    return true;
  }

  /**
   * For packed primitive values. Extracts a packed value from the tree node by its 'internal
   * index'. For example in `Bitvector(512)` the bit value at index `300` is stored at the second
   * leaf node and it's 'internal index' in this node would be `45`
   */
  SszDataT createFromPackedNode(TreeNode node, int internalIndex);

  /**
   * For packed primitive values. Packs the values to the existing node at 'internal indexes' For
   * example in `Bitvector(512)` the bit value at index `300` is stored at the second leaf node and
   * it's 'internal index' in this node would be `45`
   */
  TreeNode updatePackedNode(TreeNode srcNode, List<PackedNodeUpdate<DataT, SszDataT>> updates);

  final class PackedNodeUpdate<DataT, SszDataT extends SszPrimitive<DataT, SszDataT>> {
    private final int internalIndex;
    private final SszDataT newValue;

    public PackedNodeUpdate(int internalIndex, SszDataT newValue) {
      this.internalIndex = internalIndex;
      this.newValue = newValue;
    }

    public int getInternalIndex() {
      return internalIndex;
    }

    public SszDataT getNewValue() {
      return newValue;
    }
  }
}
