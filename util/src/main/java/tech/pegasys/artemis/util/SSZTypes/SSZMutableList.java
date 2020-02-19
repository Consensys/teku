package tech.pegasys.artemis.util.SSZTypes;

import java.util.Collection;

public interface SSZMutableList<R> extends SSZList<R>, SSZMutableCollection<R> {

  void add(R element);

  default void addAll(SSZImmutableCollection<? extends R> elements) {
    elements.forEach(this::add);
  }

  default void addAll(Collection<? extends R> elements) {
    elements.forEach(this::add);
  }
}
