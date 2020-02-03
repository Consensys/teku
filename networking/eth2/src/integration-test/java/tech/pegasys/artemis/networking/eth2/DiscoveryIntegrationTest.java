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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.ArrayWrapperList;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.discovery.Eth2DiscoveryService;
import tech.pegasys.artemis.networking.eth2.discovery.network.DiscoveryNetwork;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.util.Waiter;

public class DiscoveryIntegrationTest {

  Logger logger = LogManager.getLogger();
  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();

  @AfterEach
  public void tearDown() {
    logger.info("Starting teardown");
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
  @RepeatedTest(30)
  public void peersConnectedIndirectlyShouldDiscoveryEachOther() throws Exception {
    // initially nodeA knows only of bootnode and bootnode knows noone
    final Eth2Network bootnode = networkFactory.builder().startNetwork();
    final Eth2Network nodeA = networkFactory.builder().discoveryPeer(bootnode).startNetwork();
    logger.info("port_bootnode:" + bootnode.getNetworkConfig().getListenPort());
    logger.info("port_a:" + nodeA.getNetworkConfig().getListenPort());

    // nodeA should look for peers
    doPeering(nodeA);

    // ensure that bootnode and nodeA know about each other after one search round
    // i.e., nodeA doing a search against bootnode updates bootnode's known peer set
    Waiter.waitFor(() -> assertThat(checkPeerTable(nodeA, bootnode)).isPresent());
    Waiter.waitFor(() -> assertThat(checkPeerTable(bootnode, nodeA)).isPresent());

    // start nodeB with knowledge of bootnode
    final Eth2Network nodeB = networkFactory.builder().discoveryPeer(bootnode).startNetwork();
    logger.info("port_b:" + nodeB.getNetworkConfig().getListenPort());

    // nodeB conducts one round of search
    // bootnode's known peer set will add nodeB
    // nodeB will find nodeA from bootnode's response
    doPeering(nodeB);
    // check nodeB and bootnode know each other
    Waiter.waitFor(() -> assertThat(checkPeerTable(bootnode, nodeB)).isPresent());
    Waiter.waitFor(() -> assertThat(checkPeerTable(nodeB, bootnode)).isPresent());
    // check nodeB knows nodeA
    Waiter.waitFor(() -> assertThat(checkPeerTable(nodeA, nodeB)).isPresent());

    // nodeA should find nodeB (via bootnode)
    doPeering(nodeA);
    Waiter.waitFor(() -> assertThat(checkPeerTable(nodeB, nodeA)).isPresent());
  }

  private Optional<NodeRecordInfo> checkPeerTable(Eth2Network thisnode, Eth2Network nodeB) {
    return ((Eth2DiscoveryService) nodeB.getDiscoveryService())
        .getNodeTable()
        .getNode(
            ((Eth2DiscoveryService) thisnode.getDiscoveryService())
                .getNodeTable()
                .getHomeNode()
                .getNodeId());
  }

  private void doPeering(Eth2Network nodeB) throws InterruptedException {
    nodeB.getDiscoveryService().findPeers();
  }

  private void checkPeering(Eth2Network thisnode, Eth2Network shouldknowthisnode)
      throws InterruptedException {
    Thread.sleep(3000);
//    Waiter.waitFor(() -> assertThat(thisnode.getPeer(shouldknow.getNodeId())).isPresent());
    assertThat(thisnode.getPeer(shouldknowthisnode.getNodeId())).isPresent();
  }

  private void printNodeTable(DiscoveryNetwork discovery, String s) {
    Eth2DiscoveryService discovery_a = (Eth2DiscoveryService) discovery;
    logger.info("printnodeTable:" + s);
    List<NodeRecordInfo> closestNodes_a_2 =
        discovery_a
            .getNodeTable()
            .findClosestNodes(
                discovery_a.getNodeTable().getHomeNode().getNodeId(), Integer.MAX_VALUE);
    closestNodes_a_2.forEach(nr -> logger.info(s + ":" + nr.getNode().getPort()));
  }
}
