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

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;
import tech.pegasys.teku.ssz.tree.TreeNode;

/**
 * Base class for any SSZ structure schema like Vector, List, Container, primitive types
 * (https://github.com/ethereum/eth2.0-specs/blob/dev/ssz/simple-serialize.md#typing)
 */
public interface SszSchema<SszDataT extends SszData> extends SszType {

  @SuppressWarnings("unchecked")
  static <X extends SszData> SszSchema<X> as(final Class<X> clazz, final SszSchema<?> schema) {
    return (SszSchema<X>) schema;
  }

  /**
   * Creates a default backing binary tree for this schema
   *
   * <p>E.g. if the schema is primitive then normally just a single leaf node is created
   *
   * <p>E.g. if the schema is a complex structure with multi-level nested vectors and containers
   * then the complete tree including all descendant members subtrees is created
   */
  TreeNode getDefaultTree();

  /**
   * Creates immutable ssz structure over the tree which should correspond to this schema.
   *
   * <p>Note: if the tree structure doesn't correspond this schema that fact could only be detected
   * later during access to structure members
   */
  SszDataT createFromBackingNode(TreeNode node);

  /** Returns the default immutable structure of this scheme */
  default SszDataT getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  boolean isPrimitive();

  /**
   * For packed primitive values. Extracts a packed value from the tree node by its 'internal
   * index'. For example in `Bitvector(512)` the bit value at index `300` is stored at the second
   * leaf node and it's 'internal index' in this node would be `45`
   */
  default SszDataT createFromBackingNode(TreeNode node, int internalIndex) {
    return createFromBackingNode(node);
  }

  /**
   * For packed primitive values. Packs the value to the existing node at 'internal index' For
   * example in `Bitvector(512)` the bit value at index `300` is stored at the second leaf node and
   * it's 'internal index' in this node would be `45`
   */
  default TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, SszData newValue) {
    return newValue.getBackingNode();
  }

  default TreeNode updateBackingNode(
      TreeNode srcNode, int[] internalIndexes, SszData[] newValues, int count) {
    assert internalIndexes.length >= count && newValues.length >= count;
    TreeNode res = srcNode;
    for (int i = 0; i < count; i++) {
      res = updateBackingNode(res, internalIndexes[i], newValues[i]);
    }
    return res;
  }

  default Bytes sszSerialize(SszDataT view) {
    return sszSerializeTree(view.getBackingNode());
  }

  default int sszSerialize(SszDataT view, SszWriter writer) {
    return sszSerializeTree(view.getBackingNode(), writer);
  }

  default SszDataT sszDeserialize(SszReader reader) throws SszDeserializeException {
    return createFromBackingNode(sszDeserializeTree(reader));
  }

  default SszDataT sszDeserialize(Bytes ssz) throws SszDeserializeException {
    return sszDeserialize(SszReader.fromBytes(ssz));
  }
}
