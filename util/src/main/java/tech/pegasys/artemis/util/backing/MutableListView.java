package tech.pegasys.artemis.util.backing;

import java.util.function.Function;

public interface MutableListView<C> extends ImmutableListView<C> {

  default void update(int index, Function<C, C> mutator) {
    set(index, mutator.apply(get(index)));
  }

  default void set(int index, C value) {
    update(index, ignore -> value);
  }

  default void append(C value) {
    set(size(), value);
  }
}
