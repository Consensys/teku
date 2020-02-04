package tech.pegasys.artemis.util.backing;

import java.util.function.Function;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;

public interface CompositeView<C extends View> extends View {

  default int size() {
    return getType().getMaxLength();
  }

  C get(int index);

  void set(int index, C value);

  @Override
  CompositeViewType<? extends CompositeView<C>> getType();

  default void update(int index, Function<C, C> mutator) {
    set(index, mutator.apply(get(index)));
  }
}
