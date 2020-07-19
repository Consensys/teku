package tech.pegasys.teku.api.schema;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class BeaconStateTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final tech.pegasys.teku.datastructures.state.BeaconState beaconStateInternal = dataStructureUtil.randomBeaconState();

  @Test
  public void shouldConvertToInternalObject() {
    BeaconState beaconState = new BeaconState(beaconStateInternal);

    assertThat(beaconState.asInternalBeaconState())
        .isEqualTo(beaconStateInternal);
  }
}
