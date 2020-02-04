package tech.pegasys.artemis.util.backing.type;

import java.util.Objects;
import tech.pegasys.artemis.util.backing.CompositeView;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;

public abstract class CollectionViewType<C extends View, L extends CompositeView<C>> implements
    CompositeViewType<L> {

  private final int maxLength;
  private final ViewType<C> elementType;

  public CollectionViewType(int maxLength, ViewType<C> elementType) {
    this.maxLength = maxLength;
    this.elementType = elementType;
  }

  public int getMaxLength() {
    return maxLength;
  }

  public ViewType<C> getElementType() {
    return elementType;
  }

  @Override
  public ViewType<?> getChildType(int index) {
    return getElementType();
  }

  @Override
  public int getBitsPerElement() {
    return getElementType().getBitsSize();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CollectionViewType<?, ?> that = (CollectionViewType<?, ?>) o;
    return maxLength == that.maxLength &&
        elementType.equals(that.elementType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxLength, elementType);
  }
}
