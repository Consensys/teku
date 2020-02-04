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

package tech.pegasys.artemis.util.backing.view;

import com.google.common.primitives.UnsignedLong;
import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.type.BasicViewType;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;

public class BasicViews {

  static class PackedBasicView<C> extends AbstractBasicView<C> {
    private final C value;

    public PackedBasicView(C value, BasicViewType<? extends PackedBasicView<C>> type) {
      super(null, type);
      this.value = value;
    }

    @Override
    public C get() {
      return value;
    }

    @Override
    public TreeNode getBackingNode() {
      return TreeNodeImpl.ZERO_LEAF;
    }
  }

  public static class BitView extends PackedBasicView<Boolean> {
    public BitView(Boolean value) {
      super(value, BasicViewTypes.BIT_TYPE);
    }
  }

  public static class ByteView extends PackedBasicView<Byte> {
    public ByteView(Byte value) {
      super(value, BasicViewTypes.BYTE_TYPE);
    }
  }

  public static class PackedUnsignedLongView extends PackedBasicView<UnsignedLong> {
    public PackedUnsignedLongView(UnsignedLong value) {
      super(value, BasicViewTypes.PACKED_UNSIGNED_LONG_TYPE);
    }

    public long longValue() {
      return get().longValue();
    }

    public static PackedUnsignedLongView fromLong(long val) {
      return new PackedUnsignedLongView(UnsignedLong.valueOf(val));
    }
  }

  public static class UnsignedLongView extends AbstractBasicView<UnsignedLong> {

    public UnsignedLongView(TreeNode node) {
      super(node, BasicViewTypes.UNSIGNED_LONG_TYPE);
    }

    public UnsignedLongView(UnsignedLong val) {
      this(
          new RootImpl(
              Bytes32.rightPad(Bytes.ofUnsignedLong(val.longValue(), ByteOrder.LITTLE_ENDIAN))));
    }

    @Override
    public UnsignedLong get() {
      return UnsignedLong.valueOf(
          getBackingNode().hashTreeRoot().slice(0, 8).toLong(ByteOrder.LITTLE_ENDIAN));
    }
  }

  public static class Bytes32View extends AbstractBasicView<Bytes32> {

    public Bytes32View(TreeNode node) {
      super(node, BasicViewTypes.BYTES32_TYPE);
    }

    public Bytes32View(Bytes32 val) {
      this(new RootImpl(val));
    }

    @Override
    public Bytes32 get() {
      return getBackingNode().hashTreeRoot();
    }
  }
}
