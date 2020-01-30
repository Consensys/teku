package tech.pegasys.artemis.util.backing.type;

import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.view.ListView;

public class ListViewTypeComposite<C extends View> extends ListViewType<C, ListView<C>> {

  private final ViewType<C> elementType;

  public ListViewTypeComposite(int maxLength, ViewType<C> elementType) {
    super(maxLength);
    this.elementType = elementType;
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
    return ListView.createFromTreeNode(this, node);
  }
}
