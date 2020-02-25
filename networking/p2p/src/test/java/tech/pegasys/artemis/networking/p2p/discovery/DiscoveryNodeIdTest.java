package tech.pegasys.artemis.networking.p2p.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.p2p.discovery.DiscoveryNodeId;

class DiscoveryNodeIdTest {
  public static final Bytes NODE_ID_BYTES =
      Bytes.fromHexString("0x833246C198388F1B5E06EF1950B0A6705FBF6370E002656CDA2C6C803C06258D");

  private final DiscoveryNodeId id = new DiscoveryNodeId(NODE_ID_BYTES);

  @Test
  public void shouldSerializeToBytesCorrectly() {
    assertThat(id.toBytes()).isEqualTo(NODE_ID_BYTES);
  }

  @Test
  public void shouldSerializeToBase58Correctly() {
    assertThat(id.toBase58()).isEqualTo("9q8se7jAA4ngArgS5U1kyyYHrdfdLcmfjebUFvkQpHVE");
  }
}
