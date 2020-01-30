package tech.pegasys.artemis.util.backing;

import java.util.function.Function;

public interface CompositeView<C> extends View {

  default int size() {
    return getType().getMaxLength();
  }

  C get(int index);

  void set(int index, C value);

  default void update(int index, Function<C, C> mutator) {
    set(index, mutator.apply(get(index)));
  }

  @Override
  CompositeViewType<? extends View> getType();
}
