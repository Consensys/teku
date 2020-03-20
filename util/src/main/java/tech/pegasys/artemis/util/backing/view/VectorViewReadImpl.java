package tech.pegasys.artemis.util.backing.view;

import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.type.ViewType;
import tech.pegasys.artemis.util.cache.IntCache;

public class VectorViewReadImpl<R extends ViewRead>
    extends AbstractCompositeViewRead<VectorViewReadImpl<R>, R> implements VectorViewRead<R> {

  public VectorViewReadImpl(CompositeViewType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public VectorViewReadImpl(CompositeViewType type, TreeNode backingNode, IntCache<R> cache) {
    super(type, backingNode, cache);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected R getImpl(int index) {
    VectorViewType<R> type = getType();
    ViewType elementType = type.getElementType();
    TreeNode node =
        getBackingNode().get(type.getGeneralizedIndex(index / type.getElementsPerChunk()));
    return (R) elementType.createFromBackingNode(node, index % type.getElementsPerChunk());
  }

  @Override
  protected int sizeImpl() {
    return (int) Long.min(Integer.MAX_VALUE, getType().getMaxLength());
  }

  @Override
  public VectorViewWriteImpl<R, ?> createWritableCopy() {
    return new VectorViewWriteImpl<>(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public VectorViewType<R> getType() {
    return (VectorViewType<R>) super.getType();
  }

  @Override
  protected void checkIndex(int index) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for vector with size " + size());
    }
  }
}
