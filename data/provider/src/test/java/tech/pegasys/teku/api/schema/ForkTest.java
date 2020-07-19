package tech.pegasys.teku.api.schema;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

import static org.assertj.core.api.Assertions.assertThat;


public class ForkTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final tech.pegasys.teku.datastructures.state.Fork forkInternal = dataStructureUtil.randomFork();

  @Test
  public void shouldConvertToInternalObject() {
    Fork fork = new Fork(forkInternal);
    assertThat(fork.asInternalFork()).isEqualTo(forkInternal);
  }
}
