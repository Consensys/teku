package tech.pegasys.artemis.util.backing.view;

import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.CompositeViewWriteRef;
import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.VectorViewType;

public class DelegateVectorView<R extends ViewRead, W extends R>
    implements VectorViewWriteRef<R, W> {
  CompositeViewWriteRef<R, W> delegate;

  public DelegateVectorView(CompositeViewWriteRef<R, W> delegate) {
    this.delegate = delegate;
  }

  @Override
  public W getByRef(int index) {
    return delegate.getByRef(index);
  }

  @Override
  public void setInvalidator(
      Consumer<ViewWrite> listener) {
    delegate.setInvalidator(listener);
  }

  @Override
  public void set(int index, R value) {
    delegate.set(index, value);
  }

  @Override
  public void update(int index, Function<R, R> mutator) {
    delegate.update(index, mutator);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public VectorViewRead<R> commitChanges() {
    return new DelegateVectorView<>((CompositeViewWriteRef<R, W>) delegate.commitChanges());
  }

  @Override
  public TreeNode getBackingNode() {
    throw new UnsupportedOperationException();
//    return delegate.getBackingNode();
  }

  @Override
  public VectorViewWrite<R> createWritableCopy() {
    throw new UnsupportedOperationException();
//    return new DelegateVectorView<>((CompositeViewWriteRef<R, W>) delegate.createWritableCopy());
  }

  @Override
  public VectorViewType<R> getType() {
    throw new UnsupportedOperationException();
//    return delegate.getType();
  }

  @Override
  public Bytes32 hashTreeRoot() {
    return delegate.hashTreeRoot();
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public R get(int index) {
    return delegate.get(index);
  }
}

