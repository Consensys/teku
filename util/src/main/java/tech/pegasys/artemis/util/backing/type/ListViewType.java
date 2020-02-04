package tech.pegasys.artemis.util.backing.type;

import tech.pegasys.artemis.util.backing.ListView;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.view.ListViewImpl;

public class ListViewType<C extends View> extends CollectionViewType<C, ListView<C>> {

  public ListViewType(VectorViewType<C> vectorType) {
    this(vectorType.getElementType(), vectorType.getMaxLength());
  }

  public ListViewType(ViewType<C> elementType, int maxLength) {
    super(maxLength, elementType);
  }

  @Override
  public ListView<C> createDefault() {
    return new ListViewImpl<C>(getCompatibleVectorType().createDefault(), 0);
  }

  @Override
  public ListView<C> createFromTreeNode(TreeNode node) {
    return new ListViewImpl<>(this, node);
  }

  public VectorViewType<C> getCompatibleVectorType() {
    return new VectorViewType<>(getElementType(), getMaxLength());
  }

}
