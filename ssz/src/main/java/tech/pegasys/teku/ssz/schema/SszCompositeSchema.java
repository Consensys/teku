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

package tech.pegasys.teku.ssz.schema;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SszComposite;
import tech.pegasys.teku.ssz.tree.BranchNode;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeUtil;

/** Abstract schema of {@link SszComposite} subclasses */
public interface SszCompositeSchema<SszCompositeT extends SszComposite<?>>
    extends SszSchema<SszCompositeT> {
  int MAX_DEPTH_COMPRESSION = 8;

  @Override
  default void storeBackingNodes(final TreeNode backingNode, final BackingNodeStore store) {
    final int depth = treeDepth();
    final Bytes32[] childHashes = new Bytes32[getChunks((int) getMaxLength())];
    if (childHashes.length > 100) {
      System.out.println("WARN: " + getClass().getSimpleName() + " has a lot of children");
    }
    for (int childIndex = 0; childIndex < childHashes.length; childIndex++) {
      final TreeNode childNode = backingNode.get(getChildGeneralizedIndex(childIndex));
      childHashes[childIndex] = childNode.hashTreeRoot();
      getChildSchema(childIndex).storeBackingNodes(childNode, store);
    }
    store.storeCompressedBranch(backingNode.hashTreeRoot(), depth, childHashes);
  }

  static void storeNonZeroBranchNodes(
      final TreeNode rootNode,
      final BackingNodeStore store,
      final int remainingDepth,
      final Consumer<TreeNode> storeCompressed) {
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(rootNode.hashTreeRoot())) {
      return;
    }
    if (remainingDepth == 0) {
      storeCompressed.accept(rootNode);
      return;
    }
    final BranchNode branchNode = (BranchNode) rootNode;
    storeNonZeroBranchNodes(branchNode.left(), store, remainingDepth - 1, storeCompressed);
    storeNonZeroBranchNodes(branchNode.right(), store, remainingDepth - 1, storeCompressed);
    store.storeCompressedBranch(
        branchNode.hashTreeRoot(),
        1,
        new Bytes32[] {branchNode.left().hashTreeRoot(), branchNode.right().hashTreeRoot()});
  }

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
   * @throws IndexOutOfBoundsException if index >= getMaxLength
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
  default int getChunks(int elementCount) {
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
  default long getChildGeneralizedIndex(long elementIndex) {
    return GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, elementIndex, treeDepth());
  }

  @Override
  default boolean isPrimitive() {
    return false;
  }
}
