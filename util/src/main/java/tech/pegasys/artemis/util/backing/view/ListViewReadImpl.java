package tech.pegasys.artemis.util.backing.view;

import java.util.Arrays;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ListViewRead;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;

public class ListViewReadImpl<C extends ViewRead> implements ListViewRead<C> {

  private final ContainerViewWrite container;
  private final int size;
  private final ListViewType<C> type;
  private final VectorViewWrite<C> vector;

  @SuppressWarnings("unchecked")
  public ListViewReadImpl(ListViewType<C> type, TreeNode node) {
    ContainerViewType<ContainerViewWrite> containerViewType =
        new ContainerViewType<>(
            Arrays.asList(type.getCompatibleVectorType(), BasicViewTypes.UINT64_TYPE),
            MutableContainerImpl::new);
    this.type = type;
    this.container = containerViewType.createFromBackingNode(node);
    this.size = getSizeFromTree();
    this.vector = (VectorViewWrite<C>) container.get(0);

  }

  private int getSizeFromTree() {
    UInt64View sizeView = (UInt64View) container.get(1);
    return sizeView.get().intValue();
  }

  @Override
  public C get(int index) {
    checkIndex(index);
    return vector.get(index);
  }

  @Override
  public ListViewWrite<C> createWritableCopy() {
    return null;
  }

  @Override
  public ListViewType<C> getType() {
    return type;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public TreeNode getBackingNode() {
    return container.getBackingNode();
  }

  protected void checkIndex(int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for list with size " + size());
    }
  }
}

