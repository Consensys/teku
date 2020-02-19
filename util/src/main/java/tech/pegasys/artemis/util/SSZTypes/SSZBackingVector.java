package tech.pegasys.artemis.util.SSZTypes;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;

public class SSZBackingVector<C, R extends ViewRead> extends
    SSZAbstractCollection<C> implements SSZMutableVector<C> {

  private final VectorViewWrite<R> delegate;
  private final Function<C, R> wrapper;
  private final Function<R, C> unwrapper;

  public SSZBackingVector(Class<C> classInfo,
      VectorViewWrite<R> delegate, Function<C, R> wrapper,
      Function<R, C> unwrapper) {
    super(classInfo);
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
  public void set(int index, C element) {
    delegate.set(index, wrapper.apply(element));
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public long getMaxSize() {
    return size();
  }

  public Bytes32 hash_tree_root() {
    return delegate.hashTreeRoot();
  }
}
