package tech.pegasys.teku.api.schema;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class Eth1DataTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final tech.pegasys.teku.datastructures.blocks.Eth1Data eth1DataInternal = dataStructureUtil.randomEth1Data();
  @Test
  public void shouldConvertToInternalObject() {
    final Eth1Data eth1Data = new Eth1Data(eth1DataInternal);
    assertThat(eth1Data.asInternalEth1Data()).isEqualTo(eth1DataInternal);
  }
}
