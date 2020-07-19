package tech.pegasys.teku.api.schema;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class BLSPubKeyTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final BLSPublicKey blsPublicKey = dataStructureUtil.randomPublicKey();
  @Test
  public void shouldConvertToInternalObject() {
    final BLSPubKey blsPubKey = new BLSPubKey(blsPublicKey);
    assertThat(blsPubKey.asBLSPublicKey()).isEqualTo(blsPublicKey);
  }
}
