package tech.pegasys.artemis.util.SSZTypes;

import java.util.AbstractList;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;

public class SSZBackingList<C, R extends ViewRead> extends AbstractList<C>
    implements SSZListWrite<C> {

  private final Class<C> classInfo;
  private final ListViewWrite<R> delegate;
  private final Function<C, R> wrapper;
  private final Function<R, C> unwrapper;

  public SSZBackingList(Class<C> classInfo,
      ListViewWrite<R> delegate, Function<C, R> wrapper,
      Function<R, C> unwrapper) {
    this.classInfo = classInfo;
    this.delegate = delegate;
    this.wrapper = wrapper;
    this.unwrapper = unwrapper;
  }

  @Override
  public C get(int index) {
    return unwrapper.apply(delegate.get(index));
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public long getMaxSize() {
    return delegate.getType().getMaxLength();
  }

  @Override
  public boolean add(C c) {
    delegate.append(wrapper.apply(c));
    return true;
  }

  @Override
  public Class<C> getElementType() {
    return classInfo;
  }

  @Override
  public C set(int index, C element) {
    delegate.set(index, wrapper.apply(element));
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  public Bytes32 hash_tree_root() {
    return delegate.hashTreeRoot();
  }
}
