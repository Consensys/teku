package tech.pegasys.artemis.util.backing.view;

import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.cache.IntCache;

public class ContainerViewReadImpl extends AbstractCompositeViewRead<ContainerViewReadImpl, ViewRead>
    implements ContainerViewRead {

  public ContainerViewReadImpl(ContainerViewType<?> type) {
    this(type, type.getDefaultTree());
  }

  public ContainerViewReadImpl(ContainerViewType<?> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public ContainerViewReadImpl(
      CompositeViewType type, TreeNode backingNode, IntCache<ViewRead> cache) {
    super(type, backingNode, cache);
  }

  @Override
  protected ViewRead getImpl(int index) {
    CompositeViewType type = getType();
    TreeNode node = getBackingNode().get(type.getGeneralizedIndex(index));
    return type.getChildType(index).createFromBackingNode(node);
  }

  @Override
  public ContainerViewWrite createWritableCopy() {
    return new ContainerViewWriteImpl(this);
  }

  @Override
  protected int sizeImpl() {
    return (int) getType().getMaxLength();
  }

  @Override
  protected void checkIndex(int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for container with size " + size());
    }
  }
}
