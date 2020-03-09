/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.networking.p2p;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.artemis.network.p2p.DiscoveryNetworkFactory;
import tech.pegasys.artemis.networking.p2p.discovery.DiscoveryMethod;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.util.Waiter;

public class DiscoveryNetworkIntegrationTest {

  private final DiscoveryNetworkFactory discoveryNetworkFactory = new DiscoveryNetworkFactory();

  @Test
  public void shouldConnectToStaticPeers() throws Exception {
    final DiscoveryNetwork<Peer> network1 = discoveryNetworkFactory.builder().buildAndStart();
    final DiscoveryNetwork<Peer> network2 =
        discoveryNetworkFactory.builder().staticPeer(network1.getNodeAddress()).buildAndStart();
    assertConnected(network1, network2);
  }

  @Test
  public void shouldReconnectToStaticPeersAfterDisconnection() throws Exception {
    final DiscoveryNetwork<Peer> network1 = discoveryNetworkFactory.builder().buildAndStart();
    final DiscoveryNetwork<Peer> network2 =
        discoveryNetworkFactory.builder().staticPeer(network1.getNodeAddress()).buildAndStart();
    assertConnected(network1, network2);

    // Peers disconnect
    network1.getPeer(network2.getNodeId()).orElseThrow().disconnectImmediately();

    // But are automatically reconnected
    assertConnected(network1, network2);
  }

  @Test
  public void shouldReconnectToStaticPeersWhenAlreadyConnected() throws Exception {
    final DiscoveryNetwork<Peer> network1 = discoveryNetworkFactory.builder().buildAndStart();
    final DiscoveryNetwork<Peer> network2 =
        discoveryNetworkFactory.builder().staticPeer(network1.getNodeAddress()).buildAndStart();
    assertConnected(network1, network2);

    // Already connected, but now tell network1 to maintain a persistent connection to network2.
    network1.addStaticPeer(network2.getNodeAddress());

    network1.getPeer(network2.getNodeId()).orElseThrow().disconnectImmediately();
    assertConnected(network1, network2);

    // Check we remain connected and didn't just briefly reconnect.
    Thread.sleep(1000);
    assertConnected(network1, network2);
  }

  @ParameterizedTest(name = "shouldConnectToBootnodes - {0}")
  @EnumSource(DiscoveryMethod.class)
  public void shouldConnectToBootnodes(final DiscoveryMethod discoveryMethod) throws Exception {
    final DiscoveryNetwork<Peer> network1 =
        discoveryNetworkFactory.builder().discoveryMethod(discoveryMethod).buildAndStart();
    final DiscoveryNetwork<Peer> network2 =
        discoveryNetworkFactory
            .builder()
            .bootnode(network1.getEnr().orElseThrow())
            .discoveryMethod(discoveryMethod)
            .buildAndStart();
    assertConnected(network1, network2);
  }

  @ParameterizedTest(name = "shouldDiscoverPeers - {0}")
  @EnumSource(value = DiscoveryMethod.class)
  @Disabled // Neither discovery library is currently discovering peers correctly.
  public void shouldDiscoverPeers(final DiscoveryMethod discoveryMethod) throws Exception {
    final DiscoveryNetwork<Peer> network1 =
        discoveryNetworkFactory.builder().discoveryMethod(discoveryMethod).buildAndStart();
    final DiscoveryNetwork<Peer> network2 =
        discoveryNetworkFactory
            .builder()
            .bootnode(network1.getEnr().orElseThrow())
            .discoveryMethod(discoveryMethod)
            .buildAndStart();
    assertConnected(network1, network2);

    // Only knows about network1, but should discovery network2
    final DiscoveryNetwork<Peer> network3 =
        discoveryNetworkFactory
            .builder()
            .bootnode(network1.getEnr().orElseThrow())
            .discoveryMethod(discoveryMethod)
            .buildAndStart();
    assertConnected(network1, network3);
    assertConnected(network2, network3);
  }

  private void assertConnected(
      final DiscoveryNetwork<Peer> network1, final DiscoveryNetwork<Peer> network2) {
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeer(network2.getNodeId())).isPresent();
          assertThat(network2.getPeer(network1.getNodeId())).isPresent();
        });
  }
}
