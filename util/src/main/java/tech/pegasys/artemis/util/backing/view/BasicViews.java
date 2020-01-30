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

public class BasicViews {

  public static final ViewType<UInt64View> UINT64_TYPE = new ViewType<>() {
    @Override
    public UInt64View createDefault() {
      return createFromTreeNode(new RootImpl(Bytes32.ZERO));
    }

    @Override
    public UInt64View createFromTreeNode(TreeNode node) {
      return new UInt64View(node);
    }
  };

  public static class UInt64View implements BasicView<UnsignedLong> {
    private final TreeNode node;

    private UInt64View(TreeNode node) {
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
      return UINT64_TYPE;
    }

    @Override
    public TreeNode getBackingNode() {
      return node;
    }
  }

  public static final ViewType<Bytes32View> BYTES32_TYPE = new ViewType<>() {
    @Override
    public Bytes32View createDefault() {
      return createFromTreeNode(new RootImpl(Bytes32.ZERO));
    }

    @Override
    public Bytes32View createFromTreeNode(TreeNode node) {
      return new Bytes32View(node);
    }
  };

  public static class Bytes32View implements BasicView<Bytes32> {
    private final TreeNode node;

    private Bytes32View(TreeNode node) {
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
      return BYTES32_TYPE;
    }

    @Override
    public TreeNode getBackingNode() {
      return node;
    }
  }
}
