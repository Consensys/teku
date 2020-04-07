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

package tech.pegasys.artemis.ssz.backing.type;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import tech.pegasys.artemis.ssz.SSZTypes.Bytes4;
import tech.pegasys.artemis.ssz.backing.ViewRead;
import tech.pegasys.artemis.ssz.backing.tree.TreeNode;
import tech.pegasys.artemis.ssz.backing.view.BasicViews;

/** The collection of commonly used basic types */
public class BasicViewTypes {

  public static final BasicViewType<BasicViews.BitView> BIT_TYPE =
      new BasicViewType<>(1) {
        @Override
        public BasicViews.BitView createFromBackingNode(TreeNode node, int idx) {
          return new BasicViews.BitView((node.hashTreeRoot().get(idx / 8) & (1 << (idx % 8))) != 0);
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int idx, ViewRead newValue) {
          MutableBytes32 dest = srcNode.hashTreeRoot().mutableCopy();
          int byteIndex = idx / 8;
          int bitIndex = idx % 8;
          byte b = dest.get(byteIndex);
          if (((BasicViews.BitView) newValue).get()) {
            b = (byte) (b | (1 << bitIndex));
          } else {
            b = (byte) (b & ~(1 << bitIndex));
          }
          dest.set(byteIndex, b);
          return TreeNode.createLeafNode(dest);
        }
      };

  public static final BasicViewType<BasicViews.ByteView> BYTE_TYPE =
      new BasicViewType<>(8) {
        @Override
        public BasicViews.ByteView createFromBackingNode(TreeNode node, int internalIndex) {
          return new BasicViews.ByteView(node.hashTreeRoot().get(internalIndex));
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int index, ViewRead newValue) {
          byte[] bytes = srcNode.hashTreeRoot().toArray();
          bytes[index] = ((BasicViews.ByteView) newValue).get();
          return TreeNode.createLeafNode(Bytes32.wrap(bytes));
        }
      };

  public static final BasicViewType<BasicViews.UInt64View> UINT64_TYPE =
      new BasicViewType<>(64) {
        @Override
        public BasicViews.UInt64View createFromBackingNode(TreeNode node, int internalIndex) {
          return BasicViews.UInt64View.fromLong(
              node.hashTreeRoot().slice(internalIndex * 8, 8).toLong(ByteOrder.LITTLE_ENDIAN));
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int index, ViewRead newValue) {
          Bytes32 originalChunk = srcNode.hashTreeRoot();
          return TreeNode.createLeafNode(
              Bytes32.wrap(
                  Bytes.concatenate(
                      originalChunk.slice(0, index * 8),
                      Bytes.ofUnsignedLong(
                          ((BasicViews.UInt64View) newValue).longValue(), ByteOrder.LITTLE_ENDIAN),
                      originalChunk.slice((index + 1) * 8))));
        }
      };

  public static final BasicViewType<BasicViews.Bytes4View> BYTES4_TYPE =
      new BasicViewType<>(32) {
        @Override
        public BasicViews.Bytes4View createFromBackingNode(TreeNode node, int internalIndex) {
          return new BasicViews.Bytes4View(
              new Bytes4(node.hashTreeRoot().slice(internalIndex * 4, 4)));
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, ViewRead newValue) {
          checkArgument(
              internalIndex >= 0 && internalIndex < 8, "Invalid internal index: %s", internalIndex);
          Bytes32 originalChunk = srcNode.hashTreeRoot();
          return TreeNode.createLeafNode(
              Bytes32.wrap(
                  Bytes.concatenate(
                      originalChunk.slice(0, internalIndex * 4),
                      ((BasicViews.Bytes4View) newValue).get().getWrappedBytes(),
                      originalChunk.slice((internalIndex + 1) * 4))));
        }
      };

  public static final BasicViewType<BasicViews.Bytes32View> BYTES32_TYPE =
      new BasicViewType<>(256) {
        @Override
        public BasicViews.Bytes32View createFromBackingNode(TreeNode node, int internalIndex) {
          return new BasicViews.Bytes32View(node.hashTreeRoot());
        }

        @Override
        public TreeNode updateBackingNode(TreeNode srcNode, int internalIndex, ViewRead newValue) {
          return TreeNode.createLeafNode(((BasicViews.Bytes32View) newValue).get());
        }
      };
}
