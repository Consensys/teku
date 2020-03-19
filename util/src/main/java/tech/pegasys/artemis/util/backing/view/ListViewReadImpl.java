package tech.pegasys.artemis.util.backing.view;

import java.util.Arrays;
import tech.pegasys.artemis.util.backing.ListViewRead;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ListViewWriteImpl.ListContainerWrite;
import tech.pegasys.artemis.util.cache.Cache;

public class ListViewReadImpl<C extends ViewRead> implements ListViewRead<C> {

  static class ListContainerRead<C extends ViewRead> extends ContainerViewReadImpl {

    private static <C extends ViewRead>
        ContainerViewType<ListContainerRead<C>> vectorTypeToContainerType(
            VectorViewType<C> vectorType) {
      return new ContainerViewType<>(
          Arrays.asList(vectorType, BasicViewTypes.UINT64_TYPE), ListContainerRead::new);
    }

    public ListContainerRead(VectorViewType<C> vectorType) {
      super(vectorTypeToContainerType(vectorType));
    }

    ListContainerRead(ContainerViewType<ListContainerRead<C>> containerType, TreeNode backingNode) {
      super(containerType, backingNode);
    }

    public ListContainerRead(
        VectorViewType<C> vectorType, TreeNode backingNode, Cache<Integer, ViewRead> cache) {
      super(vectorTypeToContainerType(vectorType), backingNode, cache);
    }

    public int getSize() {
      return (int) ((UInt64View) get(1)).longValue();
    }

    public VectorViewRead<C> getData() {
      return getAny(0);
    }

    @Override
    public ListContainerWrite<C, ?> createWritableCopy() {
      return new ListContainerWrite<>(this);
    }

    VectorViewType<C> getVectorType() {
      return (VectorViewType<C>) getType().getChildType(0);
    }
  }

  private final ListViewType<C> type;
  private final ListContainerRead<C> container;
  private final int cachedSize;
  //  private final int size;
  //  private final VectorViewRead<C> vector;

  public ListViewReadImpl(ListViewType<C> type, TreeNode node) {
    this.type = type;
    this.container = new ListContainerRead<>(type.getCompatibleVectorType(), node, null);
    this.cachedSize = container.getSize();
  }

  public ListViewReadImpl(ListViewType<C> type) {
    this.type = type;
    this.container = new ListContainerRead<>(type.getCompatibleVectorType());
    this.cachedSize = container.getSize();
  }

  public ListViewReadImpl(ListViewType<C> type, ListContainerRead<C> container) {
    this.type = type;
    this.container = container;
    this.cachedSize = container.getSize();
  }

  @Override
  public C get(int index) {
    checkIndex(index);
    return container.getData().get(index);
  }

  @Override
  public ListViewWrite<C> createWritableCopy() {
    return new ListViewWriteImpl<>(getType(), container.createWritableCopy());
  }

  @Override
  public ListViewType<C> getType() {
    return type;
  }

  @Override
  public int size() {
    return cachedSize;
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
