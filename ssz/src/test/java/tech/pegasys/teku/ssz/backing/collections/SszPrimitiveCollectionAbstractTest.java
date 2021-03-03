package tech.pegasys.teku.ssz.backing.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.SszCollectionAbstractTest;
import tech.pegasys.teku.ssz.backing.SszPrimitive;

public interface SszPrimitiveCollectionAbstractTest extends SszCollectionAbstractTest {

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT, SszT>> void
      getElement_shouldReturnUnboxedElement(SszPrimitiveCollection<ElT, SszT> collection) {
    for (int i = 0; i < collection.size(); i++) {
      assertThat(collection.getElement(i)).isEqualTo(collection.get(i).get());
    }
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void getElement_shouldThrowIndexOfBounds(SszPrimitiveCollection<?, ?> collection) {
    assertThatThrownBy(() -> collection.getElement(-1))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> collection.getElement(collection.size()))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> collection
        .getElement((int) Long.min(Integer.MAX_VALUE, collection.getSchema().getMaxLength())))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }
}
