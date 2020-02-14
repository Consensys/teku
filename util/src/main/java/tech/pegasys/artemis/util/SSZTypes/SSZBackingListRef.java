package tech.pegasys.artemis.util.SSZTypes;

import java.util.AbstractList;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.ListViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;

public class SSZBackingListRef<R extends ViewRead, W extends R> extends AbstractList<R>
    implements SSZListWriteRef<R, W> {

  private final Class<? extends R> classInfo;
  private final ListViewWriteRef<R, W> delegate;

  public SSZBackingListRef(Class<? extends R> classInfo,
      ListViewWriteRef<R, W> delegate) {
    this.classInfo = classInfo;
    this.delegate = delegate;
  }

  @Override
  public W get(int index) {
    return delegate.getByRef(index);
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
  public boolean add(R c) {
    delegate.append(c);
    return true;
  }

  @Override
  public Class<? extends R> getElementType() {
    return classInfo;
  }

  @Override
  public R set(int index, R element) {
    return delegate.set(index, element);
  }

  public Bytes32 hash_tree_root() {
    return delegate.hashTreeRoot();
  }
}
