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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBitlistImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public class SszBitlistSchemaImpl extends AbstractSszListSchema<SszBit, SszBitlist>
    implements SszBitlistSchema<SszBitlist> {

  public SszBitlistSchemaImpl(long maxLength) {
    super(SszPrimitiveSchemas.BIT_SCHEMA, maxLength);
  }

  @Override
  public SszBitlist createFromBackingNode(TreeNode node) {
    return new SszBitlistImpl(this, node);
  }

  @Override
  public SszBitlist ofBits(int size, int... setBitIndexes) {
    Preconditions.checkArgument(size <= getMaxLength(), "size > maxLength");
    return SszBitlistImpl.ofBits(this, size, setBitIndexes);
  }

  @Override
  public SszBitlist createFromElements(List<? extends SszBit> elements) {
    return ofBits(
        elements.size(),
        IntStream.range(0, elements.size()).filter(i -> elements.get(i).get()).toArray());
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    int elementsCount = getLength(node);
    BytesCollector bytesCollector = new BytesCollector();
    getCompatibleVectorSchema()
        .sszSerializeVector(getVectorNode(node), bytesCollector, elementsCount);
    return bytesCollector.flushWithBoundaryBit(writer, elementsCount);
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    int availableBytes = reader.getAvailableBytes();
    // preliminary rough check
    checkSsz(
        (availableBytes - 1) * 8 <= getMaxLength(), "SSZ sequence length exceeds max type length");
    Bytes bytes = reader.read(availableBytes);
    int length = SszBitlistImpl.sszGetLengthAndValidate(bytes);
    if (length > getMaxLength()) {
      throw new SszDeserializeException("Too long bitlist");
    }
    Bytes treeBytes = SszBitlistImpl.sszTruncateLeadingBit(bytes, length);
    try (SszReader sszReader = SszReader.fromBytes(treeBytes)) {
      DeserializedData data = sszDeserializeVector(sszReader);
      return createTree(data.getDataTree(), length);
    }
  }

  @Override
  public String toString() {
    return "Bitlist[" + getMaxLength() + "]";
  }

  private static class BytesCollector implements SszWriter {

    private static class UnsafeBytes {

      private final byte[] bytes;
      private final int offset;
      private final int length;

      public UnsafeBytes(byte[] bytes, int offset, int length) {
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
      }
    }

    private final List<UnsafeBytes> bytesList = new ArrayList<>();
    private int size;

    @Override
    public void write(byte[] bytes, int offset, int length) {
      if (length == 0) {
        return;
      }
      bytesList.add(new UnsafeBytes(bytes, offset, length));
      size += length;
    }

    public int flushWithBoundaryBit(SszWriter writer, int boundaryBitOffset) {
      int bitIdx = boundaryBitOffset % 8;
      checkArgument(
          TreeUtil.bitsCeilToBytes(boundaryBitOffset) == size, "Invalid boundary bit offset");
      if (bitIdx == 0) {
        bytesList.forEach(bb -> writer.write(bb.bytes, bb.offset, bb.length));
        writer.write(new byte[] {1});
        return size + 1;
      } else {
        UnsafeBytes lastBytes = bytesList.get(bytesList.size() - 1);
        byte lastByte = lastBytes.bytes[lastBytes.offset + lastBytes.length - 1];
        byte lastByteWithBoundaryBit = (byte) (lastByte ^ (1 << bitIdx));

        for (int i = 0; i < bytesList.size() - 1; i++) {
          UnsafeBytes bb = bytesList.get(i);
          writer.write(bb.bytes, bb.offset, bb.length);
        }
        if (lastBytes.length > 1) {
          writer.write(lastBytes.bytes, lastBytes.offset, lastBytes.length - 1);
        }
        writer.write(new byte[] {lastByteWithBoundaryBit});
        return size;
      }
    }
  }
}
