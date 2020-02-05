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

package tech.pegasys.artemis.util.backing.type;

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.view.BasicViews.BitView;
import tech.pegasys.artemis.util.backing.view.BasicViews.ByteView;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.PackedUnsignedLongView;
import tech.pegasys.artemis.util.backing.view.BasicViews.UnsignedLongView;

public class BasicViewTypes {

  public static final BasicViewType<BitView> BIT_TYPE =
      new BasicViewType<>(1) {
        @Override
        public BitView createFromTreeNode(TreeNode node, int idx) {
          return new BitView((node.hashTreeRoot().get(idx / 8) & (1 << (8 - (idx % 8)))) != 0);
        }

        @Override
        public TreeNode updateTreeNode(TreeNode srcNode, int idx, View newValue) {
          MutableBytes32 dest = srcNode.hashTreeRoot().mutableCopy();
          int byteIndex = idx / 8;
          int bitIndex = idx % 8;
          byte b = dest.get(byteIndex);
          if (((BitView)newValue).get()) {
            b = (byte) (b | (1 << (8 - bitIndex)));
          } else {
            b = (byte) (b & ~(1 << (8 - bitIndex)));
          }
          dest.set(byteIndex, b);
          return new RootImpl(dest);
        }
      };

  public static final BasicViewType<ByteView> BYTE_TYPE =
      new BasicViewType<>(8) {
        @Override
        public ByteView createFromTreeNode(TreeNode node, int internalIndex) {
          return new ByteView(node.hashTreeRoot().get(internalIndex));
        }

        @Override
        public TreeNode updateTreeNode(TreeNode srcNode, int index, View newValue) {
          byte[] bytes = srcNode.hashTreeRoot().toArray();
          bytes[index] = ((ByteView)newValue).get();
          return new RootImpl(Bytes32.wrap(bytes));
        }
      };

  public static final BasicViewType<PackedUnsignedLongView> PACKED_UNSIGNED_LONG_TYPE =
      new BasicViewType<>(64) {
        @Override
        public PackedUnsignedLongView createFromTreeNode(TreeNode node, int internalIndex) {
          return PackedUnsignedLongView.fromLong(
              node.hashTreeRoot().slice(internalIndex * 8, 8).toLong(ByteOrder.LITTLE_ENDIAN));
        }

        @Override
        public TreeNode updateTreeNode(
            TreeNode srcNode, int index, View newValue) {
          Bytes32 originalChunk = srcNode.hashTreeRoot();
          return new RootImpl(
              Bytes32.wrap(
                  Bytes.concatenate(
                      originalChunk.slice(0, index * 8),
                      Bytes.ofUnsignedLong(((PackedUnsignedLongView)newValue).longValue(), ByteOrder.LITTLE_ENDIAN),
                      originalChunk.slice((index + 1) * 8))));
        }
      };

  public static final BasicViewType<UnsignedLongView> UNSIGNED_LONG_TYPE =
      new BasicViewType<>(64) {
        @Override
        public UnsignedLongView createFromTreeNode(TreeNode node, int internalIndex) {
          return new UnsignedLongView(node);
        }

        @Override
        public TreeNode updateTreeNode(TreeNode srcNode, int index, View newValue) {
          throw new UnsupportedOperationException();
        }
      };

  public static final BasicViewType<Bytes32View> BYTES32_TYPE =
      new BasicViewType<>(256) {
        @Override
        public Bytes32View createFromTreeNode(TreeNode node, int internalIndex) {
          return new Bytes32View(node);
        }

        @Override
        public TreeNode updateTreeNode(TreeNode srcNode, int index, View newValue) {
          throw new UnsupportedOperationException();
        }
      };
}
