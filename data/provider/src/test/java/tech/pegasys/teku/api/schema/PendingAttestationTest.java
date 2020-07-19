package tech.pegasys.teku.api.schema;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class PendingAttestationTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final tech.pegasys.teku.datastructures.state.PendingAttestation attestationInternal = dataStructureUtil.randomPendingAttestation();

  @Test
  public void shouldConvertToInternalObject() {
    final PendingAttestation pendingAttestation = new PendingAttestation(attestationInternal);
    assertThat(pendingAttestation.asInternalPendingAttestation()).isEqualTo(attestationInternal);
  }
}
