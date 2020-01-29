package tech.pegasys.artemis.util.backing.type;

import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.view.ListView;

public class ListViewType<C extends View> implements ViewType<ListView<C>> {
  private final int maxLength;
  private final ViewType<C> elementType;

  public ListViewType(int maxLength, ViewType<C> elementType) {
    this.maxLength = maxLength;
    this.elementType = elementType;
  }

  public int getMaxLength() {
    return maxLength;
  }

  public ViewType<C> getElementType() {
    return elementType;
  }

  @Override
  public ListView<C> createDefault() {
    return ListView.createDefault(this, elementType.createDefault().getBackingNode());
  }

  @Override
  public ListView<C> createFromTreeNode(TreeNode node) {
    return ListView.createDefault(this, node);
  }
}
