package tech.pegasys.artemis.util.backing.view;

import tech.pegasys.artemis.util.backing.BasicView;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNode;

public abstract class AbstractBasicView<C> implements BasicView<C> {
  private final TreeNode node;
  private final ViewType<? extends AbstractBasicView> type;

  public AbstractBasicView(TreeNode node,
      ViewType<? extends AbstractBasicView> type) {
    this.node = node;
    this.type = type;
  }

  @Override
  public ViewType<? extends AbstractBasicView> getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return node;
  }
}
