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

package tech.pegasys.teku.ssz.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxCompare;

import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation;
import tech.pegasys.teku.ssz.backing.tree.TreeNodeImpl.LeafNodeImpl;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil.ZeroLeafNode;

/**
 * Leaf node of a tree which contains 'bytes32' value. This node type corresponds to the 'Root' node
 * in the spec:
 * https://github.com/protolambda/eth-merkle-trees/blob/master/typing_partials.md#structure
 */
public interface LeafNode extends TreeNode, LeafDataNode {

  int MAX_BYTE_SIZE = 32;
  int MAX_BIT_SIZE = MAX_BYTE_SIZE * 8;

  /**
   * Pre-allocated leaf nodes with the data consisting of 0, 1, 2, ..., 32 zero bytes Worth to
   * mention that {@link TreeNode#hashTreeRoot()} for all these nodes return the same value {@link
   * Bytes32#ZERO}
   */
  LeafNode[] ZERO_LEAVES =
      IntStream.rangeClosed(0, MAX_BYTE_SIZE).mapToObj(ZeroLeafNode::new).toArray(LeafNode[]::new);

  /** The {@link LeafNode} with empty data */
  LeafNode EMPTY_LEAF = ZERO_LEAVES[0];

  /** Creates a basic Leaf node instance with the data <= 32 bytes */
  static LeafNode create(Bytes data) {
    return new LeafNodeImpl(data);
  }

  /**
   * Returns only data bytes without zero right padding (unlike {@link #hashTreeRoot()}) E.g. if a
   * {@code LeafNode} corresponds to a contained UInt64 field, then {@code getData()} returns only 8
   * bytes corresponding to the field value If a {@code Vector[Byte, 48]} is stored across two
   * {@code LeafNode}s then the second node {@code getData} would return just the last 16 bytes of
   * the vector (while {@link #hashTreeRoot()} would return zero padded 32 bytes)
   */
  @Override
  Bytes getData();

  /** LeafNode hash tree root is the leaf data right padded to 32 bytes */
  @Override
  default Bytes32 hashTreeRoot() {
    return Bytes32.rightPad(getData());
  }

  /**
   * @param target generalized index. Should be equal to 1
   * @return this node if 'target' == 1
   * @throws IllegalArgumentException if 'target' != 1
   */
  @NotNull
  @Override
  default TreeNode get(long target) {
    checkArgument(target == 1, "Invalid root index: %s", target);
    return this;
  }

  @Override
  default boolean iterate(
      long thisGeneralizedIndex, long startGeneralizedIndex, TreeVisitor visitor) {
    if (gIdxCompare(thisGeneralizedIndex, startGeneralizedIndex) == NodeRelation.Left) {
      return true;
    } else {
      return visitor.visit(this, thisGeneralizedIndex);
    }
  }

  @Override
  default TreeNode updated(long target, Function<TreeNode, TreeNode> nodeUpdater) {
    checkArgument(target == 1, "Invalid root index: %s", target);
    return nodeUpdater.apply(this);
  }
}
