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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** The collection of commonly used basic types */
public interface SszPrimitiveSchemas {

  AbstractSszPrimitiveSchema<Void, SszNone> NONE_SCHEMA =
      new AbstractSszPrimitiveSchema<>(0) {
        @Override
        public SszNone createFromLeafBackingNode(LeafDataNode node, int internalIndex) {
          return SszNone.INSTANCE;
        }

        @Override
        protected TreeNode updateBackingNode(
            TreeNode srcNode, int internalIndex, SszData newValue) {
          return srcNode;
        }

        @Override
        public SszNone boxed(Void rawValue) {
          return SszNone.INSTANCE;
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.EMPTY_LEAF;
        }

        @Override
        public String toString() {
          return "None";
        }
      };

  AbstractSszPrimitiveSchema<Boolean, SszBit> BIT_SCHEMA =
      new AbstractSszPrimitiveSchema<>(1) {
        @Override
        public SszBit createFromLeafBackingNode(LeafDataNode node, int idx) {
          return SszBit.of((node.getData().get(idx / 8) & (1 << (idx % 8))) != 0);
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int idx, SszData newValue) {
          int byteIndex = idx / 8;
          int bitIndex = idx % 8;
          Bytes originalBytes = ((LeafNode) srcNode).getData();
          byte b = byteIndex < originalBytes.size() ? originalBytes.get(byteIndex) : 0;
          boolean bit = ((SszBit) newValue).get();
          if (bit) {
            b = (byte) (b | (1 << bitIndex));
          } else {
            b = (byte) (b & ~(1 << bitIndex));
          }
          Bytes newBytes = updateExtending(originalBytes, byteIndex, Bytes.of(b));
          return LeafNode.create(newBytes);
        }

        @Override
        public SszBit boxed(Boolean rawValue) {
          return SszBit.of(rawValue);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[1];
        }

        @Override
        protected LeafNode createNodeFromSszBytes(final Bytes bytes) {
          final byte data = bytes.get(0);
          if (data != 0 && data != 1) {
            throw new SszDeserializeException("Invalid bit value: " + bytes);
          }
          return super.createNodeFromSszBytes(bytes);
        }

        @Override
        public String toString() {
          return "Bit";
        }
      };

  AbstractSszPrimitiveSchema<Byte, SszByte> BYTE_SCHEMA =
      new AbstractSszPrimitiveSchema<>(8) {
        @Override
        public SszByte createFromLeafBackingNode(LeafDataNode node, int internalIndex) {
          return SszByte.of(node.getData().get(internalIndex));
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int index, SszData newValue) {
          byte aByte = ((SszByte) newValue).get();
          Bytes curVal = ((LeafNode) srcNode).getData();
          Bytes newBytes = updateExtending(curVal, index, Bytes.of(aByte));
          return LeafNode.create(newBytes);
        }

        @Override
        public SszByte boxed(Byte rawValue) {
          return SszByte.of(rawValue);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[1];
        }

        @Override
        public String toString() {
          return "Byte";
        }
      };

  AbstractSszPrimitiveSchema<UInt64, SszUInt64> UINT64_SCHEMA =
      new AbstractSszPrimitiveSchema<>(64) {
        @Override
        public SszUInt64 createFromLeafBackingNode(LeafDataNode node, int internalIndex) {
          Bytes leafNodeBytes = node.getData();
          try {
            Bytes elementBytes = leafNodeBytes.slice(internalIndex * 8, 8);
            return SszUInt64.of(UInt64.fromLongBits(elementBytes.toLong(ByteOrder.LITTLE_ENDIAN)));
          } catch (Exception e) {
            // additional info to track down the bug https://github.com/PegaSysEng/teku/issues/2579
            String info =
                "Refer to https://github.com/PegaSysEng/teku/issues/2579 if see this exception. ";
            info += "internalIndex = " + internalIndex;
            info += ", leafNodeBytes: " + leafNodeBytes.getClass().getSimpleName();
            try {
              info += ", leafNodeBytes = " + leafNodeBytes.copy();
            } catch (Exception ex) {
              info += "(" + ex + ")";
            }
            try {
              info += ", leafNodeBytes[] = " + Arrays.toString(leafNodeBytes.toArray());
            } catch (Exception ex) {
              info += "(" + ex + ")";
            }
            throw new RuntimeException(info, e);
          }
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int index, SszData newValue) {
          Bytes uintBytes =
              Bytes.ofUnsignedLong(((SszUInt64) newValue).longValue(), ByteOrder.LITTLE_ENDIAN);
          Bytes curVal = ((LeafNode) srcNode).getData();
          Bytes newBytes = updateExtending(curVal, index * 8, uintBytes);
          return LeafNode.create(newBytes);
        }

        @Override
        public TreeNode updatePackedNode(
            TreeNode srcNode, List<PackedNodeUpdate<UInt64, SszUInt64>> updates) {
          if (updates.size() == 4) {
            byte[] data = new byte[32];
            for (int i = 0; i < 4; i++) {
              long longValue = updates.get(i).getNewValue().longValue();
              int off = i * 8;
              data[off + 0] = (byte) longValue;
              data[off + 1] = (byte) (longValue >> 8);
              data[off + 2] = (byte) (longValue >> 16);
              data[off + 3] = (byte) (longValue >> 24);
              data[off + 4] = (byte) (longValue >> 32);
              data[off + 5] = (byte) (longValue >> 40);
              data[off + 6] = (byte) (longValue >> 48);
              data[off + 7] = (byte) (longValue >> 56);
            }
            return LeafNode.create(Bytes.wrap(data));
          } else {
            return super.updatePackedNode(srcNode, updates);
          }
        }

        @Override
        public SszUInt64 boxed(UInt64 rawValue) {
          return SszUInt64.of(rawValue);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[8];
        }

        @Override
        public String toString() {
          return "UInt64";
        }
      };

  AbstractSszPrimitiveSchema<UInt256, SszUInt256> UINT256_SCHEMA =
      new AbstractSszPrimitiveSchema<>(256) {
        @Override
        public SszUInt256 createFromLeafBackingNode(LeafDataNode node, int internalIndex) {
          // reverse() is due to LE -> BE conversion
          return SszUInt256.of(UInt256.fromBytes(node.getData().reverse()));
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, SszData newValue) {
          // reverse() is due to BE -> LE conversion
          return LeafNode.create(((SszUInt256) newValue).get().toBytes().reverse());
        }

        @Override
        public SszUInt256 boxed(UInt256 rawValue) {
          return SszUInt256.of(rawValue);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[32];
        }

        @Override
        public String toString() {
          return "UInt256";
        }
      };

  AbstractSszPrimitiveSchema<Bytes4, SszBytes4> BYTES4_SCHEMA =
      new AbstractSszPrimitiveSchema<>(32) {
        @Override
        public SszBytes4 createFromLeafBackingNode(LeafDataNode node, int internalIndex) {
          return SszBytes4.of(new Bytes4(node.getData().slice(internalIndex * 4, 4)));
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, SszData newValue) {
          checkArgument(
              internalIndex >= 0 && internalIndex < 8, "Invalid internal index: %s", internalIndex);
          Bytes bytes = ((SszBytes4) newValue).get().getWrappedBytes();
          Bytes curVal = ((LeafNode) srcNode).getData();
          Bytes newBytes = updateExtending(curVal, internalIndex * 4, bytes);
          return LeafNode.create(newBytes);
        }

        @Override
        public SszBytes4 boxed(Bytes4 rawValue) {
          return SszBytes4.of(rawValue);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[4];
        }

        @Override
        public String toString() {
          return "Bytes4";
        }
      };

  AbstractSszPrimitiveSchema<Bytes32, SszBytes32> BYTES32_SCHEMA =
      new AbstractSszPrimitiveSchema<>(256) {
        @Override
        public SszBytes32 createFromLeafBackingNode(LeafDataNode node, int internalIndex) {
          return SszBytes32.of(node.hashTreeRoot());
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, SszData newValue) {
          return LeafNode.create(((SszBytes32) newValue).get());
        }

        @Override
        public SszBytes32 boxed(Bytes32 rawValue) {
          return SszBytes32.of(rawValue);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[32];
        }

        @Override
        public String toString() {
          return "Bytes32";
        }
      };

  private static Bytes updateExtending(Bytes origBytes, int origOff, Bytes newBytes) {
    if (origOff == origBytes.size()) {
      return Bytes.wrap(origBytes, newBytes);
    } else {
      final MutableBytes dest;
      if (origOff + newBytes.size() > origBytes.size()) {
        dest = MutableBytes.create(origOff + newBytes.size());
        origBytes.copyTo(dest, 0);
      } else {
        dest = origBytes.mutableCopy();
      }
      newBytes.copyTo(dest, origOff);
      return dest;
    }
  }
}
