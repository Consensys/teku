package tech.pegasys.artemis.util.backing.type;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;

public class BasicViewTypes {

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
}
