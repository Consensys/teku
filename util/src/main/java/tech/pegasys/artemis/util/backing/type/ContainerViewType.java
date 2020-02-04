package tech.pegasys.artemis.util.backing.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import tech.pegasys.artemis.util.backing.ContainerView;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl;

public class ContainerViewType<C extends ContainerView> implements CompositeViewType<C> {

  private final List<ViewType<?>> childrenTypes;
  private final BiFunction<ContainerViewType<C>, TreeNode, C> instanceCtor;

  public ContainerViewType(
      List<ViewType<?>> childrenTypes,
      BiFunction<ContainerViewType<C>, TreeNode, C> instanceCtor) {
    this.childrenTypes = childrenTypes;
    this.instanceCtor = instanceCtor;
  }

  @Override
  public C createDefault() {
    return createFromTreeNode(createDefaultTree());
  }

  public TreeNode createDefaultTree() {
    List<TreeNode> defaultChildren = new ArrayList<>(getMaxLength());
    for (int i = 0; i < getMaxLength(); i++) {
      defaultChildren.add(getChildType(i).createDefault().getBackingNode());
    }
    return TreeNodeImpl.createTree(defaultChildren);
  }

  @Override
  public ViewType<?> getChildType(int index) {
    return childrenTypes.get(index);
  }

  @Override
  public C createFromTreeNode(TreeNode node) {
    return instanceCtor.apply(this, node);
  }

  @Override
  public int getMaxLength() {
    return childrenTypes.size();
  }

  @Override
  public int getBitsPerElement() {
    return 256;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ContainerViewType<?> that = (ContainerViewType<?>) o;
    return childrenTypes.equals(that.childrenTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(childrenTypes);
  }
}
