/*
 * Copyright Consensys Software Inc., 2022
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

import tech.pegasys.teku.infrastructure.ssz.SszComposite;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.StoringUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

/** Abstract schema of {@link SszComposite} subclasses */
public interface SszCompositeSchema<SszCompositeT extends SszComposite<?>>
    extends SszSchema<SszCompositeT> {

  /**
   * Returns the maximum number of elements in ssz structures of this scheme. For structures with
   * fixed number of children (like Containers and Vectors) their size should always be equal to
   * maxLength
   */
  long getMaxLength();

  /**
   * Returns the child schema at index. For homogeneous structures (like Vector, List) the returned
   * schema is the same for any index For heterogeneous structures (like Container) each child has
   * individual schema
   *
   * @throws IndexOutOfBoundsException if index >= getMaxLength for all schemas except Profile
   *     (stable container). A Profile can have more schemas than the actual maxLength, so for those
   *     schemas the exception will be thrown when index is >= than number of defined children
   *     schema.
   */
  SszSchema<?> getChildSchema(int index);

  /**
   * Return the number of elements that may be stored in a single tree node This value is 1 for all
   * types except of packed basic lists/vectors
   */
  default int getElementsPerChunk() {
    return 1;
  }

  /**
   * Returns the maximum number of this ssz structure backed subtree 'leaf' nodes required to store
   * maxLength elements
   */
  default long maxChunks() {
    return (getMaxLength() - 1) / getElementsPerChunk() + 1;
  }

  /**
   * Returns then number of chunks (i.e. leaf nodes) to store {@code elementCount} child elements
   * Returns a number lower than {@code elementCode} only in case of packed basic types collection
   */
  default int getChunks(final int elementCount) {
    return (elementCount - 1) / getElementsPerChunk() + 1;
  }

  /** Returns the backed binary tree depth to store maxLength elements */
  default int treeDepth() {
    return Long.bitCount(treeWidth() - 1);
  }

  /** Returns the backed binary tree width to store maxLength elements */
  default long treeWidth() {
    return TreeUtil.nextPowerOf2(maxChunks());
  }

  /**
   * Returns binary backing tree generalized index corresponding to child element index
   *
   * @see TreeNode#get(long)
   */
  default long getChildGeneralizedIndex(final long elementIndex) {
    return GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, elementIndex, treeDepth());
  }

  @Override
  default boolean isPrimitive() {
    return false;
  }

  @Override
  default void storeBackingNodes(
      final TreeNodeStore nodeStore,
      final int maxBranchLevelsSkipped,
      final long rootGIndex,
      final TreeNode node) {
    final int childDepth = treeDepth();
    if (childDepth == 0) {
      // Only one child so wrapper is omitted
      storeChildNode(nodeStore, maxBranchLevelsSkipped, rootGIndex, node);
      return;
    }
    final long lastUsefulGIndex =
        GIndexUtil.gIdxChildGIndex(rootGIndex, maxChunks() - 1, childDepth);
    StoringUtil.storeNodesToDepth(
        nodeStore,
        maxBranchLevelsSkipped,
        node,
        rootGIndex,
        childDepth,
        lastUsefulGIndex,
        (targetDepthNode, targetDepthGIndex) ->
            storeChildNode(nodeStore, maxBranchLevelsSkipped, targetDepthGIndex, targetDepthNode));
  }

  void storeChildNode(
      TreeNodeStore nodeStore, int maxBranchLevelsSkipped, long gIndex, TreeNode node);
}
