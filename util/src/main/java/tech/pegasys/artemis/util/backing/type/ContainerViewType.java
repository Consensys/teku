package tech.pegasys.artemis.util.backing.type;

import java.util.List;
import tech.pegasys.artemis.util.backing.CompositeViewType;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.view.ContainerView;

public class ContainerViewType implements CompositeViewType<ContainerView> {

  private final List<ViewType<?>> childrenTypes;

  public ContainerViewType(List<ViewType<?>> childrenTypes) {
    this.childrenTypes = childrenTypes;
  }

  @Override
  public ContainerView createDefault() {
    return null;
  }

  @Override
  public ViewType<?> getChildType(int index) {
    return childrenTypes.get(index);
  }

  @Override
  public ContainerView createFromTreeNode(TreeNode node) {
    return new ContainerView(this, node);
  }

  @Override
  public int getMaxLength() {
    return childrenTypes.size();
  }
}
