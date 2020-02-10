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
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
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

  public static class PackedUInt64View extends PackedBasicView<UnsignedLong> {
    public static PackedUInt64View fromLong(long val) {
      return new PackedUInt64View(UnsignedLong.valueOf(val));
    }

    public PackedUInt64View(UnsignedLong value) {
      super(value, BasicViewTypes.PACKED_UINT64_TYPE);
    }

    public long longValue() {
      return get().longValue();
    }
  }

  public static class UInt64View extends AbstractBasicView<UnsignedLong> {

    public static UInt64View fromLong(long val) {
      return new UInt64View(UnsignedLong.valueOf(val));
    }

    public UInt64View(TreeNode node) {
      super(node, BasicViewTypes.UINT64_TYPE);
    }

    public UInt64View(UnsignedLong val) {
      this(
          new RootImpl(
              Bytes32.rightPad(Bytes.ofUnsignedLong(val.longValue(), ByteOrder.LITTLE_ENDIAN))));
    }

    public long longValue() {
      return get().longValue();
    }

    @Override
    public UnsignedLong get() {
      return UnsignedLong.fromLongBits(
          getBackingNode().hashTreeRoot().slice(0, 8).toLong(ByteOrder.LITTLE_ENDIAN));
    }
  }

  public static class Bytes4View extends AbstractBasicView<Bytes4> {

    public Bytes4View(TreeNode node) {
      super(node, BasicViewTypes.BYTES4_TYPE);
    }

    public Bytes4View(Bytes4 val) {
      this(new RootImpl(Bytes32.rightPad(val.getWrappedBytes())));
    }

    @Override
    public Bytes4 get() {
      return new Bytes4(getBackingNode().hashTreeRoot().slice(0, 4));
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
