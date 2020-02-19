package tech.pegasys.artemis.util.SSZTypes;

import tech.pegasys.artemis.util.backing.ViewRead;

public interface SSZMutableRefList<R extends ViewRead, W extends R> extends SSZMutableList<R> {

  @Override
  W get(int index);
}
