package tech.pegasys.artemis.util.backing;

import tech.pegasys.artemis.util.backing.type.VectorViewType;

public interface VectorView<C extends View> extends CompositeView<C> {

  @Override
  VectorViewType<C> getType();
}
