package tech.pegasys.artemis.util.backing.view;

import java.util.function.Consumer;
import java.util.function.Function;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.ListWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;

public class BasicListWrapper<C, V extends ViewRead> implements ListWrite<C> {
  private final ListViewWrite<V> wrappedViewList;
  private final Function<C, V> wrapper;
  private final Function<V, C> unwrapper;

  public BasicListWrapper(ListViewWrite<V> wrappedViewList, Function<C, V> wrapper,
      Function<V, C> unwrapper) {
    this.wrappedViewList = wrappedViewList;
    this.wrapper = wrapper;
    this.unwrapper = unwrapper;
  }

  @Override
  public C set(int index, C value) {
    wrappedViewList.set(index, wrapper.apply(value));
    return null;
  }

  @Override
  public void clear() {
    wrappedViewList.clear();
  }

  @Override
  public void setIvalidator(Consumer<ViewWrite> listener) {
    wrappedViewList.setIvalidator(listener);
  }

  @Override
  public int size() {
    return wrappedViewList.size();
  }

  @Override
  public C get(int index) {
    return unwrapper.apply(wrappedViewList.get(index));
  }

  @Override
  public CompositeViewType getType() {
    return null;
  }
}
