package tech.pegasys.artemis.util.SSZTypes;

import tech.pegasys.artemis.util.backing.VectorViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;

public interface SSZMutableVectorRef<R extends ViewRead, W extends R> extends SSZMutableVector<R>,
    VectorViewWriteRef<R, W> {

  @Override
  default int size() {
    return VectorViewWriteRef.super.size();
  }

  @Override
  default W get(int index) {
    return getByRef(index);
  }

}
