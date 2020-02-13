package tech.pegasys.artemis.util.SSZTypes;

import tech.pegasys.artemis.util.backing.ListViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;

public interface SSZListWriteRef<R extends ViewRead, W extends R> extends SSZListWrite<R>,
    ListViewWriteRef<R, W> {

  @Override
  default boolean isEmpty() {
    return ListViewWriteRef.super.isEmpty();
  }

  @Override
  default W get(int index) {
    return getByRef(index);
  }
}
