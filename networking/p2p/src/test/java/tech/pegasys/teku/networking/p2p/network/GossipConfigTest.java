package tech.pegasys.teku.networking.p2p.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GossipConfigTest {
  @Test
  void shouldUseCorrectSeenTtl() {
    // Should be heartbeat interval * 550
    assertThat(GossipConfig.DEFAULT_SEEN_TTL.toMillis()).isEqualTo(700 * 550);
  }
}
