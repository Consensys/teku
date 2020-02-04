package tech.pegasys.artemis.util.backing.type;

import tech.pegasys.artemis.util.backing.VectorView;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl;
import tech.pegasys.artemis.util.backing.view.VectorViewImpl;

public class VectorViewType<C extends View> extends CollectionViewType<C, VectorView<C>> {

  VectorViewType(ViewType<C> elementType, int maxLength) {
    super(maxLength, elementType);
  }

  @Override
  public VectorView<C> createDefault() {
    return createFromTreeNode(TreeNodeImpl
        .createZeroTree(treeDepth(), getElementType().createDefault().getBackingNode()));
  }

  @Override
  public VectorView<C> createFromTreeNode(TreeNode node) {
    return new VectorViewImpl<>(this, node);
  }
}
