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

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
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

  TreeNode loadBackingNodes(BackingNodeSource source, Bytes32 rootHash);

  void storeBackingNodes(TreeNode backingNode, BackingNodeStore store);

  /** Returns the default immutable structure of this scheme */
  default SszDataT getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  boolean isPrimitive();

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

  interface BackingNodeSource {
    CompressedBranchInfo getBranchData(Bytes32 root);

    Bytes getLeafData(Bytes32 root);
  }

  interface BackingNodeStore {
    void storeCompressedBranch(Bytes32 root, int depth, Bytes32[] children);

    void storeLeafNode(LeafDataNode node);
  }

  class CompressedBranchInfo {
    private final int depth;
    private final Bytes32[] children;

    public CompressedBranchInfo(final int depth, final Bytes32[] children) {
      this.depth = depth;
      this.children = children;
    }

    public int getDepth() {
      return depth;
    }

    public Bytes32[] getChildren() {
      return children;
    }

    public static Bytes serialize(final int depth, final Bytes32[] children) {
      return Bytes.wrap(Bytes.ofUnsignedInt(depth), Bytes.wrap(children));
    }

    public static CompressedBranchInfo deserialize(final Bytes data) {
      checkArgument(
          (data.size() - Integer.BYTES) % Bytes32.SIZE == 0,
          "Branch data was an invalid length %s",
          data);
      // Take unsigned int from front (depth)
      final int depth = data.getInt(0);
      // Then split remaining into 32 byte chunks (children)
      final Bytes childHashes = data.slice(Integer.BYTES);
      final Bytes32[] children = new Bytes32[childHashes.size() / Bytes32.SIZE];
      for (int i = 0; i < children.length; i++) {
        final Bytes32 child = Bytes32.wrap(childHashes.slice(i * Bytes32.SIZE, Bytes32.SIZE));
        children[i] = child;
      }
      return new CompressedBranchInfo(depth, children);
    }
  }
}
