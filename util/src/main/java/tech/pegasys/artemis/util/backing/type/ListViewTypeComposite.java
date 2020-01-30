package tech.pegasys.artemis.util.backing.type;

import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.view.CompositeListView;

public class ListViewTypeComposite<C extends View> extends ListViewType<C, CompositeListView<C>> {

  private final ViewType<C> elementType;

  public ListViewTypeComposite(int maxLength, ViewType<C> elementType) {
    super(maxLength);
    this.elementType = elementType;
  }

  public ViewType<C> getElementType() {
    return elementType;
  }

  @Override
  public CompositeListView<C> createDefault() {
    return CompositeListView.createDefault(this);
  }

  @Override
  public CompositeListView<C> createFromTreeNode(TreeNode node) {
    return CompositeListView.createFromTreeNode(this, node);
  }
}
