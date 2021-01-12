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

import java.util.Optional;
import tech.pegasys.teku.ssz.backing.Utils;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SszReader;

/**
 * Base class for any SSZ type like Vector, List, Container, basic types
 * (https://github.com/ethereum/eth2.0-specs/blob/dev/ssz/simple-serialize.md#typing)
 */
public interface ViewType<V extends ViewRead> extends SSZType {

  static Optional<ViewType<?>> getType(Class<?> clazz) {
    return Utils.getSszType(clazz);
  }

  /**
   * Creates a default backing binary tree for this type E.g. if the type is basic then normally
   * just a single leaf node is created E.g. if the type is a complex structure with multi-level
   * nested vectors and containers then the complete tree including all descendant members subtrees
   * is created
   */
  TreeNode getDefaultTree();

  /**
   * Creates immutable View over the tree which should correspond to this type If the tree structure
   * doesn't correspond this type that fact could only be detected later during access to View
   * members
   */
  V createFromBackingNode(TreeNode node);

  /** Creates a default immutable View */
  default ViewRead getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  /**
   * Returns the number of bits the element of this type occupies in a tree node More correct
   * definition is: how many elements of this type one tree node may contain All complex types
   * occupies the whole tree node and their bitsize assumed to be 256 Normally the bitsize < 256 is
   * for basic types that can be packed into a single leaf node
   */
  int getBitsSize();

  /**
   * For packed basic values. Extracts a packed value from the tree node by its 'internal index'.
   * For example in `Bitvector(512)` the bit value at index `300` is stored at the second leaf node
   * and it's 'internal index' in this node would be `45`
   */
  default ViewRead createFromBackingNode(TreeNode node, int internalIndex) {
    return createFromBackingNode(node);
  }

  /**
   * For packed basic values. Packs the value to the existing node at 'internal index' For example
   * in `Bitvector(512)` the bit value at index `300` is stored at the second leaf node and it's
   * 'internal index' in this node would be `45`
   */
  default TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, ViewRead newValue) {
    return newValue.getBackingNode();
  }

  default V sszDeserialize(SszReader reader) {
    return createFromBackingNode(sszDeserializeTree(reader));
  }
}
