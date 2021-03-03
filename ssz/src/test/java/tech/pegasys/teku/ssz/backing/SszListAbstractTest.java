package tech.pegasys.teku.ssz.backing;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public interface SszListAbstractTest extends SszCollectionAbstractTest {

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void size_shouldBeLessOrEqualThanMaxLength(SszList<?> data) {
    Assertions.assertThat((long) data.size()).isLessThanOrEqualTo(data.getSchema().getMaxLength());
  }
}
