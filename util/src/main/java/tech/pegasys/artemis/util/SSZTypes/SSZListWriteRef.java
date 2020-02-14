package tech.pegasys.artemis.util.SSZTypes;

import tech.pegasys.artemis.util.backing.ViewRead;

public interface SSZListWriteRef<R extends ViewRead, W extends R> extends SSZListWrite<R> {

  @Override
  W get(int index);
}
