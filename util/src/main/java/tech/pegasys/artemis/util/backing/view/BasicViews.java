package tech.pegasys.artemis.util.backing.view;

import com.google.common.primitives.UnsignedLong;
import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.BasicView;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;

public class BasicViews {

  public static class UInt64View implements BasicView<UnsignedLong> {
    private final TreeNode node;

    public UInt64View(TreeNode node) {
      this.node = node;
    }

    public UInt64View(UnsignedLong val) {
      this.node = new RootImpl(
          Bytes32.rightPad(Bytes.ofUnsignedLong(val.longValue(), ByteOrder.LITTLE_ENDIAN)));
    }

    @Override
    public UnsignedLong get() {
      return UnsignedLong.valueOf(node.hashTreeRoot().slice(0, 8).toLong(ByteOrder.LITTLE_ENDIAN));
    }

    @Override
    public ViewType<? extends View> getType() {
      return BasicViewTypes.UINT64_TYPE;
    }

    @Override
    public TreeNode getBackingNode() {
      return node;
    }
  }

  public static class Bytes32View implements BasicView<Bytes32> {
    private final TreeNode node;

    public Bytes32View(TreeNode node) {
      this.node = node;
    }

    public Bytes32View(Bytes32 val) {
      this.node = new RootImpl(val);
    }

    @Override
    public Bytes32 get() {
      return node.hashTreeRoot();
    }

    @Override
    public ViewType<? extends View> getType() {
      return BasicViewTypes.BYTES32_TYPE;
    }

    @Override
    public TreeNode getBackingNode() {
      return node;
    }
  }
}
