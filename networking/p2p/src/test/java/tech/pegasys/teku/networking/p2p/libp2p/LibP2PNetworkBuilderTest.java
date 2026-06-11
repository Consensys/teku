/*
 * Copyright Consensys Software Inc., 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.networking.p2p.libp2p;

import static org.assertj.core.api.Assertions.assertThat;

import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

class LibP2PNetworkBuilderTest {

  // Defaults from NetworkConfig: TCP 9000/9090 (v4/v6), QUIC 9001/9091 (v4/v6)
  private static final NodeId NODE_ID =
      new LibP2PNodeId(PeerId.fromBase58("16Uiu2HAmFxCpRh2nZevFR3KGXJ3jhpixMYFSuawqKZyZYHrYoiK5"));

  private NetworkConfig.Builder singleStackConfig() {
    return NetworkConfig.builder()
        .networkInterface("127.0.0.1")
        .advertisedIp(Optional.of("127.0.0.1"));
  }

  private NetworkConfig.Builder dualStackConfig() {
    return NetworkConfig.builder()
        .networkInterfaces(List.of("127.0.0.1", "::1"))
        .advertisedIps(Optional.of(List.of("127.0.0.1", "::1")));
  }

  // ---- listenPorts ----

  @Test
  void buildListenPorts_singleStackTcpOnly() {
    final NetworkConfig config = singleStackConfig().build();
    assertThat(LibP2PNetworkBuilder.buildListenPorts(config)).containsExactly(9000);
  }

  @Test
  void buildListenPorts_singleStackTcpAndQuic() {
    final NetworkConfig config = singleStackConfig().quicEnabled(true).build();
    assertThat(LibP2PNetworkBuilder.buildListenPorts(config)).containsExactly(9000, 9001);
  }

  @Test
  void buildListenPorts_singleStackQuicOnly() {
    // Regression: single-IP QUIC must not report the IPv6 ports.
    final NetworkConfig config = singleStackConfig().quicEnabled(true).tcpEnabled(false).build();
    assertThat(LibP2PNetworkBuilder.buildListenPorts(config)).containsExactly(9001);
  }

  @Test
  void buildListenPorts_dualStackTcpOnly() {
    final NetworkConfig config = dualStackConfig().build();
    assertThat(LibP2PNetworkBuilder.buildListenPorts(config)).containsExactly(9000, 9090);
  }

  @Test
  void buildListenPorts_dualStackTcpAndQuic() {
    // Regression: dual-stack QUIC must report all four ports, not just two.
    final NetworkConfig config = dualStackConfig().quicEnabled(true).build();
    assertThat(LibP2PNetworkBuilder.buildListenPorts(config))
        .containsExactly(9000, 9001, 9090, 9091);
  }

  // ---- advertised addresses ----

  @Test
  void buildAdvertisedAddresses_quicEnabledTcpDisabled_doesNotAdvertiseTcp() {
    // Regression: a disabled transport must not be advertised to peers (e.g. in the ENR).
    final NetworkConfig config = singleStackConfig().quicEnabled(true).tcpEnabled(false).build();
    final List<String> addresses =
        asStrings(LibP2PNetworkBuilder.buildAdvertisedAddresses(config, NODE_ID));
    assertThat(addresses).hasSize(1);
    assertThat(addresses.get(0)).contains("/udp/9001/quic-v1");
    assertThat(addresses).noneMatch(addr -> addr.contains("/tcp/"));
  }

  @Test
  void buildAdvertisedAddresses_tcpAndQuicEnabled_advertisesBoth() {
    final NetworkConfig config = singleStackConfig().quicEnabled(true).build();
    final List<String> addresses =
        asStrings(LibP2PNetworkBuilder.buildAdvertisedAddresses(config, NODE_ID));
    assertThat(addresses).hasSize(2);
    assertThat(addresses).anyMatch(addr -> addr.contains("/tcp/9000"));
    assertThat(addresses).anyMatch(addr -> addr.contains("/udp/9001/quic-v1"));
  }

  @Test
  void buildAdvertisedAddresses_dualStackTcpAndQuic_advertisesAllFour() {
    final NetworkConfig config = dualStackConfig().quicEnabled(true).build();
    final List<String> addresses =
        asStrings(LibP2PNetworkBuilder.buildAdvertisedAddresses(config, NODE_ID));
    assertThat(addresses).hasSize(4);
    assertThat(addresses).anyMatch(addr -> addr.contains("/tcp/9000"));
    assertThat(addresses).anyMatch(addr -> addr.contains("/udp/9001/quic-v1"));
    assertThat(addresses).anyMatch(addr -> addr.contains("/tcp/9090"));
    assertThat(addresses).anyMatch(addr -> addr.contains("/udp/9091/quic-v1"));
  }

  // ---- listen addresses ----

  @Test
  void buildListenAddresses_quicEnabledTcpDisabled_doesNotListenOnTcp() {
    final NetworkConfig config = singleStackConfig().quicEnabled(true).tcpEnabled(false).build();
    final List<String> addresses = List.of(LibP2PNetworkBuilder.buildListenAddresses(config));
    assertThat(addresses).hasSize(1);
    assertThat(addresses.get(0)).contains("/udp/9001/quic-v1");
    assertThat(addresses).noneMatch(addr -> addr.contains("/tcp/"));
  }

  @Test
  void buildListenAddresses_tcpAndQuicEnabled_listensOnBoth() {
    final NetworkConfig config = singleStackConfig().quicEnabled(true).build();
    final List<String> addresses = List.of(LibP2PNetworkBuilder.buildListenAddresses(config));
    assertThat(addresses).hasSize(2);
    assertThat(addresses).anyMatch(addr -> addr.contains("/tcp/9000"));
    assertThat(addresses).anyMatch(addr -> addr.contains("/udp/9001/quic-v1"));
  }

  private static List<String> asStrings(final List<Multiaddr> addresses) {
    return addresses.stream().map(Multiaddr::toString).toList();
  }
}
