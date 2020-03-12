package tech.pegasys.artemis.util.backing.view;

import java.util.ArrayList;
import tech.pegasys.artemis.util.backing.VectorViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.VectorViewType;

public class VectorViewWriteImpl<R extends ViewRead, W extends R>
    extends AbstractCompositeViewWrite1<VectorViewWriteImpl<R, W>, R, W>
    implements VectorViewWriteRef<R, W> {


  public VectorViewWriteImpl(AbstractCompositeViewRead<?, R> backingImmutableView) {
    super(backingImmutableView);
  }

  @Override
  protected AbstractCompositeViewRead<?, R> createViewRead(
      TreeNode backingNode, ArrayList<R> viewCache) {
    return new VectorViewReadImpl<>(getType(), backingNode, viewCache);
  }

  @Override
  public VectorViewType<R> getType() {
    return (VectorViewType<R>) super.getType();
  }

  @Override
  public VectorViewReadImpl<R> commitChanges() {
    return (VectorViewReadImpl<R>) super.commitChanges();
  }

  @Override
  protected void checkIndex(int index, boolean set) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for vector with size " + size());
    }
  }
}
