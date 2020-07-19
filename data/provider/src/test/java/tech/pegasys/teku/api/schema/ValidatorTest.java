package tech.pegasys.teku.api.schema;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidatorTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final tech.pegasys.teku.datastructures.state.Validator validatorInternal = dataStructureUtil.randomValidator();
  @Test
  public void shouldConvertToInternalObject() {
    final Validator validator = new Validator(validatorInternal);
    assertThat(validator.asInternalValidator()).isEqualTo(validatorInternal);
  }
}
