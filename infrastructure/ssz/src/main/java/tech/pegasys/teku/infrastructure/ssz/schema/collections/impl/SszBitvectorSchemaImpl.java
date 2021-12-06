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

package tech.pegasys.teku.infrastructure.ssz.schema.collections.impl;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBitvectorImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public class SszBitvectorSchemaImpl extends AbstractSszVectorSchema<SszBit, SszBitvector>
    implements SszBitvectorSchema<SszBitvector> {

  public SszBitvectorSchemaImpl(long length) {
    super(SszPrimitiveSchemas.BIT_SCHEMA, length);
    checkArgument(length > 0, "Invalid Bitlist length");
  }

  @Override
  public SszBitvector createFromBackingNode(TreeNode node) {
    return new SszBitvectorImpl(this, node);
  }

  @Override
  public SszBitvector ofBits(int... setBitIndexes) {
    return SszBitvectorImpl.ofBits(this, setBitIndexes);
  }

  @Override
  public SszBitvector createFromElements(List<? extends SszBit> elements) {
    return ofBits(IntStream.range(0, elements.size()).filter(i -> elements.get(i).get()).toArray());
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    return sszSerializeVector(node, writer, getLength());
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    checkSsz(
        reader.getAvailableBytes() == TreeUtil.bitsCeilToBytes(getLength()),
        "SSZ length doesn't match Bitvector size");

    DeserializedData data = sszDeserializeVector(reader);
    if (getLength() % 8 > 0) {
      // for BitVector we need to check that all 'unused' bits in the last byte are 0
      int usedBitCount = getLength() % 8;
      if (data.getLastSszByte().orElseThrow() >>> usedBitCount != 0) {
        throw new SszDeserializeException("Invalid Bitvector ssz: trailing bits are not 0");
      }
    }
    return data.getDataTree();
  }

  @Override
  public String toString() {
    return "Bitvector[" + getMaxLength() + "]";
  }
}
