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

package tech.pegasys.teku.networking.p2p;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.network.p2p.DiscoveryNetworkFactory;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;

public class DiscoveryNetworkIntegrationTest {
  private final DiscoveryNetworkFactory discoveryNetworkFactory = new DiscoveryNetworkFactory();

  @AfterEach
  public void tearDown() throws InterruptedException, ExecutionException, TimeoutException {
    discoveryNetworkFactory.stopAll();
  }

  @Test
  public void shouldConnectToStaticPeers() throws Exception {
    final DiscoveryNetwork<?> network1 = discoveryNetworkFactory.builder().buildAndStart();
    final DiscoveryNetwork<?> network2 =
        discoveryNetworkFactory.builder().staticPeer(network1.getNodeAddress()).buildAndStart();
    assertConnected(network1, network2);
  }

  @Test
  public void shouldReconnectToStaticPeersAfterDisconnection() throws Exception {
    final DiscoveryNetwork<?> network1 = discoveryNetworkFactory.builder().buildAndStart();
    final DiscoveryNetwork<?> network2 =
        discoveryNetworkFactory.builder().staticPeer(network1.getNodeAddress()).buildAndStart();
    assertConnected(network1, network2);

    // Peers disconnect
    network1
        .getPeer(network2.getNodeId())
        .orElseThrow()
        .disconnectImmediately(Optional.empty(), true);

    // But are automatically reconnected
    assertConnected(network1, network2);
  }

  @Test
  public void shouldReconnectToStaticPeersWhenAlreadyConnected() throws Exception {
    final DiscoveryNetwork<?> network1 = discoveryNetworkFactory.builder().buildAndStart();
    final DiscoveryNetwork<?> network2 =
        discoveryNetworkFactory.builder().staticPeer(network1.getNodeAddress()).buildAndStart();
    assertConnected(network1, network2);

    // Already connected, but now tell network1 to maintain a persistent connection to network2.
    network1.addStaticPeer(network2.getNodeAddress());

    network1
        .getPeer(network2.getNodeId())
        .orElseThrow()
        .disconnectImmediately(Optional.empty(), true);
    assertConnected(network1, network2);

    // Check we remain connected and didn't just briefly reconnect.
    Thread.sleep(1000);
    assertConnected(network1, network2);
  }

  @Test
  public void shouldConnectToBootnodes() throws Exception {
    final DiscoveryNetwork<?> network1 = discoveryNetworkFactory.builder().buildAndStart();
    final DiscoveryNetwork<?> network2 =
        discoveryNetworkFactory.builder().bootnode(network1.getEnr().orElseThrow()).buildAndStart();
    assertConnected(network1, network2);
  }

  @Test
  @Disabled("Discovery library still buggy")
  public void shouldDiscoverPeers() throws Exception {
    final DiscoveryNetwork<?> network1 = discoveryNetworkFactory.builder().buildAndStart();
    final DiscoveryNetwork<?> network2 =
        discoveryNetworkFactory.builder().bootnode(network1.getEnr().orElseThrow()).buildAndStart();
    assertConnected(network1, network2);

    // Only knows about network1, but should discovery network2
    final DiscoveryNetwork<?> network3 =
        discoveryNetworkFactory.builder().bootnode(network1.getEnr().orElseThrow()).buildAndStart();
    assertConnected(network1, network3);
    assertConnected(network2, network3);
  }

  private void assertConnected(
      final DiscoveryNetwork<?> network1, final DiscoveryNetwork<?> network2) {
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeer(network2.getNodeId())).isPresent();
          assertThat(network2.getPeer(network1.getNodeId())).isPresent();
        },
        3,
        TimeUnit.MINUTES);
  }
}
