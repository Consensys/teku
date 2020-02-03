package tech.pegasys.artemis.util.backing.view;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import tech.pegasys.artemis.util.backing.CompositeViewType;
import tech.pegasys.artemis.util.backing.ContainerView;
import tech.pegasys.artemis.util.backing.ListView;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.VectorView;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;

public class CListView<C> implements ListView<C> {


  static interface ListWrapper<C> extends ContainerView {

    VectorView<C> getData();

    void setData(VectorView<C> data);

    int getSize();

    void setSize(int size);
  }

  static class ListContainer<C> {
    VectorView<C> data;

  }

  private ContainerViewImpl container;
  private AbstractVectorView<C> data;

  public CListView(AbstractVectorView<C> data, int size) {
    this.data = data;
    ContainerViewType<ContainerViewImpl> containerViewType = new ContainerViewType<>(
        Arrays.asList(data.getType(), BasicViewTypes.UINT64_TYPE), ContainerViewImpl::new);
    container = containerViewType.createDefault();
    container.set(0, data);
    container.set(1, new UInt64View(UnsignedLong.valueOf(size)));
  }

  @Override
  public int size() {
    UInt64View sizeView = (UInt64View) container.get(1);
    return sizeView.get().intValue();
  }

  @Override
  public C get(int index) {
    return data.get(index);
  }

  @Override
  public void set(int index, C value) {
    int size = size();
    checkArgument((index >= 0 && index < size) || (index == size && index < getType().getMaxLength()),
        "Index out of bounds: %s, size=%s", index, size());

    if (index == size) {
      container.set(1, new UInt64View(UnsignedLong.valueOf(size + 1)));
    }

    container.update(0, view -> {
      VectorView<C> vector = (VectorView<C>) view;
      vector.set(index, value);
      return vector;
    });
  }

  @Override
  public CompositeViewType<? extends View> getType() {
    // TODO return the right type
    return data.getType();
  }

  @Override
  public TreeNode getBackingNode() {
    return container.getBackingNode();
  }
}
