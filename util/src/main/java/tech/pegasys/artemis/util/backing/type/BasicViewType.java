package tech.pegasys.artemis.util.backing.type;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;

public abstract class BasicViewType<C extends View> implements ViewType<C> {

  private final int bitsSize;

  public BasicViewType(int bitsSize) {
    this.bitsSize = bitsSize;
  }

  public int getBitsSize() {
    return bitsSize;
  }

  @Override
  public C createDefault() {
    return createFromTreeNode(new RootImpl(Bytes32.ZERO));
  }

  @Override
  public C createFromTreeNode(TreeNode node) {
    return createFromTreeNode(node, 0);
  }
}
