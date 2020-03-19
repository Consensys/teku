package tech.pegasys.artemis.util.backing.view;

import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.ContainerViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.cache.Cache;

public class ContainerViewWriteImpl
    extends AbstractCompositeViewWrite1<ContainerViewWriteImpl, ViewRead, ViewWrite>
    implements ContainerViewWriteRef {

  public ContainerViewWriteImpl(AbstractCompositeViewRead<?, ViewRead> backingImmutableView) {
    super(backingImmutableView);
  }

  @Override
  protected AbstractCompositeViewRead<?, ViewRead> createViewRead(
      TreeNode backingNode, Cache<Integer, ViewRead> viewCache) {
    return new ContainerViewReadImpl(getType(), backingNode, viewCache);
  }

  @Override
  public ContainerViewRead commitChanges() {
    return (ContainerViewRead) super.commitChanges();
  }

  @Override
  public ViewWrite createWritableCopy() {
    return super.createWritableCopy();
  }

  @Override
  protected void checkIndex(int index, boolean set) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for container with size " + size());
    }
  }
}
