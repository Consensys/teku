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

package tech.pegasys.teku.reference.phase0.ssz_generic.containers;

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class UInt16PrimitiveSchema extends AbstractSszPrimitiveSchema<Integer, SszUInt16> {

  public static AbstractSszPrimitiveSchema<Integer, SszUInt16> UINT16_SCHEMA =
      new UInt16PrimitiveSchema();

  private UInt16PrimitiveSchema() {
    super(16);
  }

  @Override
  public SszUInt16 createFromLeafBackingNode(LeafDataNode node, int internalIndex) {
    // reverse() is due to LE -> BE conversion
    Bytes leafNodeBytes = node.getData();
    Bytes elementBytes = leafNodeBytes.slice(internalIndex * 2, 2);
    return SszUInt16.of(elementBytes.toInt(ByteOrder.LITTLE_ENDIAN));
  }

  @Override
  public TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, SszData newValue) {
    final Integer intValue = ((SszUInt16) newValue).get();
    return LeafNode.create(Bytes.ofUnsignedInt(intValue, ByteOrder.LITTLE_ENDIAN));
  }

  @Override
  public SszUInt16 boxed(Integer rawValue) {
    return SszUInt16.of(rawValue);
  }

  @Override
  public TreeNode getDefaultTree() {
    return LeafNode.ZERO_LEAVES[16];
  }

  @Override
  public String toString() {
    return "UInt16";
  }
}
