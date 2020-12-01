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

package tech.pegasys.teku.ssz.backing.type;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.BasicViews.BitView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes4View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;

/** The collection of commonly used basic types */
public class BasicViewTypes {
  public static final BasicViewType<BitView> BIT_TYPE =
      new BasicViewType<>(1) {
        @Override
        public BitView createFromBackingNode(TreeNode node, int idx) {
          return BitView.viewOf((node.hashTreeRoot().get(idx / 8) & (1 << (idx % 8))) != 0);
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int idx, ViewRead newValue) {
          int byteIndex = idx / 8;
          int bitIndex = idx % 8;
          Bytes originalBytes = ((LeafNode) srcNode).getData();
          byte b = byteIndex < originalBytes.size() ? originalBytes.get(byteIndex) : 0;
          boolean bit = ((BitView) newValue).get();
          if (bit) {
            b = (byte) (b | (1 << bitIndex));
          } else {
            b = (byte) (b & ~(1 << bitIndex));
          }
          Bytes newBytes = updateExtending(originalBytes, byteIndex, Bytes.of(b));
          return LeafNode.create(newBytes);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[1];
        }
      };

  public static final BasicViewType<ByteView> BYTE_TYPE =
      new BasicViewType<>(8) {
        @Override
        public ByteView createFromBackingNode(TreeNode node, int internalIndex) {
          return new ByteView(node.hashTreeRoot().get(internalIndex));
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int index, ViewRead newValue) {
          byte aByte = ((ByteView) newValue).get();
          Bytes curVal = ((LeafNode) srcNode).getData();
          Bytes newBytes = updateExtending(curVal, index, Bytes.of(aByte));
          return LeafNode.create(newBytes);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[1];
        }
      };

  public static final BasicViewType<UInt64View> UINT64_TYPE =
      new BasicViewType<>(64) {
        @Override
        public UInt64View createFromBackingNode(TreeNode node, int internalIndex) {
          Bytes32 leafNodeBytes = node.hashTreeRoot();
          try {
            Bytes elementBytes = leafNodeBytes.slice(internalIndex * 8, 8);
            return UInt64View.fromLong(elementBytes.toLong(ByteOrder.LITTLE_ENDIAN));
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
        public TreeNode updateBackingNode(TreeNode srcNode, int index, ViewRead newValue) {
          Bytes uintBytes =
              Bytes.ofUnsignedLong(((UInt64View) newValue).longValue(), ByteOrder.LITTLE_ENDIAN);
          Bytes curVal = ((LeafNode) srcNode).getData();
          Bytes newBytes = updateExtending(curVal, index * 8, uintBytes);
          return LeafNode.create(newBytes);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[8];
        }
      };

  public static final BasicViewType<Bytes4View> BYTES4_TYPE =
      new BasicViewType<>(32) {
        @Override
        public Bytes4View createFromBackingNode(TreeNode node, int internalIndex) {
          return new Bytes4View(new Bytes4(node.hashTreeRoot().slice(internalIndex * 4, 4)));
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, ViewRead newValue) {
          checkArgument(
              internalIndex >= 0 && internalIndex < 8, "Invalid internal index: %s", internalIndex);
          Bytes bytes = ((Bytes4View) newValue).get().getWrappedBytes();
          Bytes curVal = ((LeafNode) srcNode).getData();
          Bytes newBytes = updateExtending(curVal, internalIndex * 4, bytes);
          return LeafNode.create(newBytes);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[4];
        }
      };

  public static final BasicViewType<Bytes32View> BYTES32_TYPE =
      new BasicViewType<>(256) {
        @Override
        public Bytes32View createFromBackingNode(TreeNode node, int internalIndex) {
          return new Bytes32View(node.hashTreeRoot());
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, ViewRead newValue) {
          return LeafNode.create(((Bytes32View) newValue).get());
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[32];
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
