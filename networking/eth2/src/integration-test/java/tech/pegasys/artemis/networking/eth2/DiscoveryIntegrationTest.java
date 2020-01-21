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

package tech.pegasys.artemis.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.discovery.network.DiscoveryNetwork;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.util.Waiter;

public class DiscoveryIntegrationTest {

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldDiscoverBootPeer() throws Exception {
    final Eth2Network network1 = networkFactory.builder().startNetwork();
    final Eth2Network network2 = networkFactory.builder().discoveryPeer(network1).startNetwork();

    // check that discovery boot peers have been added to its node table
    DiscoveryNetwork discoveryService = network2.getDiscoveryService();
    assertTrue(discoveryService.streamPeers().count() > 0);

    final Eth2Peer[] peer1 = new Eth2Peer[1];
    Waiter.waitFor(() -> peer1[0] = network2.getPeer(network1.getNodeId()).orElseThrow());
    Waiter.waitFor(() -> assertThat(peer1[0].isConnected()).isTrue());
  }

  @Test
  public void peersConnectedIndirectlyShouldDiscoveryEachOther() throws Exception {
    final Eth2Network bootnode = networkFactory.builder().startNetwork();

    // Setup network A with bootnode
    final Eth2Network nodeA = networkFactory.builder().discoveryPeer(bootnode).startNetwork();
    Waiter.waitFor(() -> assertThat(bootnode.getPeer(nodeA.getNodeId()).isPresent()));

    // Setup network B with same bootnode
    final Eth2Network nodeB = networkFactory.builder().discoveryPeer(bootnode).startNetwork();
    Waiter.waitFor(() -> assertThat(bootnode.getPeer(nodeB.getNodeId()).isPresent()));

    // A and B should find each other through the bootnode
    Waiter.waitFor(() -> assertThat(nodeA.getPeer(nodeB.getNodeId())).isPresent());
    Waiter.waitFor(() -> assertThat(nodeB.getPeer(nodeA.getNodeId())).isPresent());
  }
}
