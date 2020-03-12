package tech.pegasys.artemis.util.backing.view;

import java.util.ArrayList;
import tech.pegasys.artemis.util.backing.ContainerViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;
import tech.pegasys.artemis.util.backing.tree.TreeNode;

public class ContainerViewWriteImpl
    extends AbstractCompositeViewWrite1<ContainerViewWriteImpl, ViewRead, ViewWrite>
    implements ContainerViewWriteRef {

  public ContainerViewWriteImpl(AbstractCompositeViewRead<?, ViewRead> backingImmutableView) {
    super(backingImmutableView);
  }

  @Override
  protected AbstractCompositeViewRead<?, ViewRead> createViewRead(
      TreeNode backingNode, ArrayList<ViewRead> viewCache) {
    return new ContainerViewReadImpl(getType(), backingNode, viewCache);
  }

  @Override
  protected void checkIndex(int index, boolean set) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for container with size " + size());
    }
  }
}
