package tech.pegasys.artemis.util.backing.view;

import com.google.common.primitives.UnsignedLong;
import java.util.function.Consumer;
import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.ContainerViewWriteRef;
import tech.pegasys.artemis.util.backing.ListViewRead;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.ListViewWriteRef;
import tech.pegasys.artemis.util.backing.VectorViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;

public class ListViewWriteImpl<R extends ViewRead, W extends R>
    implements ListViewWriteRef<R, W> {

  private final ContainerViewWriteRef listContainer;
  private final VectorViewWriteRef<R, W> vector;
  private final ListViewType<R> type;
  private int size;

  @SuppressWarnings("unchecked")
  public ListViewWriteImpl(ContainerViewRead listContainer) {
    this.listContainer = (ContainerViewWriteRef) listContainer.createWritableCopy();
    this.vector = (VectorViewWriteRef<R, W>) this.listContainer.getByRef(0);
    this.type = new ListViewType<>((VectorViewType<R>)this.listContainer.getType().getChildType(0));
    this.size = getSizeFromTree();
  }

  private int getSizeFromTree() {
    UInt64View sizeView = (UInt64View) listContainer.get(1);
    return sizeView.get().intValue();
  }

  @Override
  public ListViewType<R> getType() {
    return type;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public R get(int index) {
    checkIndex(index, false);
    return vector.get(index);
  }

  @Override
  public W getByRef(int index) {
    checkIndex(index, false);
    return vector.getByRef(index);
  }

  @Override
  public ListViewRead<R> commitChanges() {
    return new ListViewReadImpl<R>(listContainer.commitChanges());
  }

  @Override
  public void setInvalidator(Consumer<ViewWrite> listener) {
    listContainer.setInvalidator(listener);
  }

  @Override
  public void set(int index, R value) {
    checkIndex(index, true);
    if (index == size()) {
      size++;
      listContainer.set(1, new UInt64View(UnsignedLong.valueOf(size())));
    }
    vector.set(index, value);
  }

  @Override
  public void clear() {
    listContainer.clear();
  }

  protected void checkIndex(int index, boolean set) {
    if ((!set && index >= size())
        || (set && (index > size() || index >= getType().getMaxLength()))) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for list with size " + size());
    }
  }

  @Override
  public ListViewWrite<R> createWritableCopy() {
    throw new UnsupportedOperationException("Creating a copy from writable list is not supported");
  }
}
