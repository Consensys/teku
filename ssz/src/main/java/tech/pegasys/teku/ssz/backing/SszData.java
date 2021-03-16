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

package tech.pegasys.teku.ssz.backing;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SszWriter;

/**
 * Base class of immutable views over Binary Backing Tree ({@link TreeNode}) Overlay views concept
 * described here:
 * https://github.com/protolambda/eth-merkle-trees/blob/master/typing_partials.md#views
 */
public interface SszData extends Merkleizable, SimpleOffsetSerializable {

  /**
   * Creates a corresponding writeable copy of this immutable structure Any modifications made to
   * the returned copy affect neither this structure nor its descendant structures
   */
  SszMutableData createWritableCopy();

  default boolean isWritableSupported() {
    return true;
  }

  /** Gets the schema of this structure */
  SszSchema<? extends SszData> getSchema();

  /** Returns Backing Tree this structure is backed by */
  TreeNode getBackingNode();

  @Override
  default Bytes32 hashTreeRoot() {
    return getBackingNode().hashTreeRoot();
  }

  @Override
  default Bytes sszSerialize() {
    return getSchema().sszSerializeTree(getBackingNode());
  }

  @Override
  default int sszSerialize(SszWriter writer) {
    return getSchema().sszSerializeTree(getBackingNode(), writer);
  }
}
