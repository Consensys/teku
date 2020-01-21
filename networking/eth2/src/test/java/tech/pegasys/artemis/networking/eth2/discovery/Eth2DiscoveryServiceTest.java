/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.networking.eth2.discovery;

import static org.ethereum.beacon.discovery.schema.EnrField.IP_V4;
import static org.ethereum.beacon.discovery.schema.EnrField.UDP_V4;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import java.net.InetAddress;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkFactory;
import tech.pegasys.artemis.networking.eth2.discovery.network.DiscoveryPeer;
import tech.pegasys.artemis.networking.eth2.discovery.network.DiscoveryPeerSubscriberImpl;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.util.async.SafeFuture;

@SuppressWarnings("UnstableApiUsage")
class Eth2DiscoveryServiceTest {

  private EventBus eventBus;

  //  private Eth2DiscoveryService mockDiscoveryManager;
  private static P2PNetwork<?> mockNetwork;
  private static Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();

  @BeforeAll
  static void doBeforeAll() {
    mockNetwork = mock(P2PNetwork.class);
    doReturn(SafeFuture.completedFuture(true)).when(mockNetwork).start();
    doReturn(SafeFuture.completedFuture(true)).when(mockNetwork).connect(any());
  }

  @BeforeEach
  void doBefore() {
    eventBus = new EventBus();
  }

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  void testEventBusRegistration() {
    // cleared for now
  }

  @Test
  void testMockNetworkUsage() throws Exception {
    Eth2Network mockEth2Network = mock(Eth2Network.class);
    doReturn(SafeFuture.completedFuture(true)).when(mockEth2Network).start();

    Eth2Network eth2Network = networkFactory.builder().startNetwork();

    doReturn(mockNetwork).when(mockEth2Network).getP2PNetwork();

    Eth2DiscoveryService serviceMock = mock(Eth2DiscoveryService.class);

    NodeTable nt = ((Eth2DiscoveryService) eth2Network.getDiscoveryService()).getNodeTable();
    EventBus localeventBus =
        ((Eth2DiscoveryService) eth2Network.getDiscoveryService()).getEventBus();
    doReturn(nt).when(serviceMock).getNodeTable();
    doReturn(localeventBus).when(serviceMock).getEventBus();

    //    serviceMock.subscribePeerDiscovery(new DiscoveryPeerSubscriberImpl(mockNetwork));
    DiscoveryPeerSubscriberImpl sip = new DiscoveryPeerSubscriberImpl(mockNetwork);
    localeventBus.register(sip);

    final String remoteHostEnr =
        "-IS4QJxZ43ITU3AsQxvwlkyzZvImNBH9CFu3yxMFWOK5rddgb0WjtIOBlPzs1JOlfi6YbM6Em3Ueu5EW-IdoPynMj4QBgmlkgnY0gmlwhKwSAAOJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCIys";
    NodeRecord remoteNodeRecord = NodeRecordFactory.DEFAULT.fromBase64(remoteHostEnr);
    NodeRecordInfo remoteNodeRecordInfo = NodeRecordInfo.createDefault(remoteNodeRecord);
    serviceMock.getNodeTable().save(remoteNodeRecordInfo);
    InetAddress byAddress =
        InetAddress.getByAddress(((Bytes) remoteNodeRecord.get(IP_V4)).toArray());

    mockNetwork.start().reportExceptions();

    verify(mockNetwork)
        .connect(
            "/ip4/"
                + byAddress.getHostAddress()
                + "/tcp/"
                + (int) remoteNodeRecord.get(UDP_V4)
                + "/p2p/"
                + DiscoveryPeer.fromNodeRecord(remoteNodeRecord).getNodeIdString());
  }

  @Test
  void nodeTableIntegrationTest() throws Exception {
    Eth2Network network1 = networkFactory.builder().startNetwork();

    Eth2DiscoveryService discoveryService =
        new Eth2DiscoveryService(network1.getNetworkConfig(), eventBus);

    DiscoveryPeerSubscriberImpl sip = new DiscoveryPeerSubscriberImpl(network1);
    eventBus.register(sip);

    final String remoteHostEnr =
        "-IS4QJxZ43ITU3AsQxvwlkyzZvImNBH9CFu3yxMFWOK5rddgb0WjtIOBlPzs1JOlfi6YbM6Em3Ueu5EW-IdoPynMj4QBgmlkgnY0gmlwhKwSAAOJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCIys";
    NodeRecord remoteNodeRecord = NodeRecordFactory.DEFAULT.fromBase64(remoteHostEnr);
    NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(remoteNodeRecord);
    discoveryService.getNodeTable().save(nodeRecordInfo);

    Assertions.assertTrue(
        discoveryService.getNodeTable().getNode(nodeRecordInfo.getNode().getNodeId()).isPresent());

    networkFactory.stopAll();
  }
}
