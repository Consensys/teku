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

package tech.pegasys.teku.ssz.backing.schema;

import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.sos.SszField;

/** Abstract schema of {@link tech.pegasys.teku.ssz.backing.SszComposite} subclasses */
public interface SszCompositeSchema<SszCompositeT extends SszData>
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
   * @throws IndexOutOfBoundsException if index >= getMaxLength
   */
  SszSchema<?> getChildSchema(int index);

  /** Returns the field by name, if such a field exists. Otherwise returns null. */
  SszField getField(String fieldName);

  /**
   * Get the index of a field by name
   *
   * @param fieldName
   * @return The index if it exists, otherwise -1
   */
  int getFieldIndex(String fieldName);

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
  default long getGeneralizedIndex(long elementIndex) {
    return treeWidth() + elementIndex;
  }

  @Override
  default int getBitsSize() {
    return 256;
  }
}
