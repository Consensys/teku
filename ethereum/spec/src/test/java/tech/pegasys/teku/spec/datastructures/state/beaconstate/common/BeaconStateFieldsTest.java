package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconStateFieldsTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldNotCauseModificationsWhenCopyingCommonFields() {
    final BeaconState source = dataStructureUtil.randomBeaconState();
    final BeaconState result =
        source.updated(target -> BeaconStateFields.copyCommonFieldsFromSource(target, source));

    assertThat(result).isEqualTo(source);
  }
}
