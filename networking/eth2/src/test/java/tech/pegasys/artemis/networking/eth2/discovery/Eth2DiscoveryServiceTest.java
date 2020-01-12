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
import io.libp2p.etc.encode.Base58;
import java.net.InetAddress;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkFactory;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.service.serviceutils.Service.State;
import tech.pegasys.artemis.util.async.SafeFuture;

@SuppressWarnings("UnstableApiUsage")
class Eth2DiscoveryServiceTest {

  private EventBus eventBus;

  private Eth2DiscoveryService mockDiscoveryManager;
  private P2PNetwork<?> mockNetwork;

  @BeforeEach
  void doBefore() {
    eventBus = new EventBus();
    mockNetwork = mock(P2PNetwork.class);
    mockDiscoveryManager = mock(Eth2DiscoveryService.class);
  }

  @Test
  void testEventBusRegistration() {
    DiscoveryRequest discoveryRequest = new DiscoveryRequest(2);
    mockDiscoveryManager.setEventBus(eventBus);
    eventBus.register(mockDiscoveryManager);
    eventBus.post(discoveryRequest);
    //    verify(mockDiscoveryManager).onDiscoveryRequest(new DiscoveryRequest(2));
  }

  @Test
  void testMockNetworkUsage() throws Exception {
    // mocked discovery manager
    doReturn(SafeFuture.completedFuture(true)).when(mockNetwork).start();
    doReturn(SafeFuture.completedFuture(true)).when(mockNetwork).connect(any());

    // set local service node
    Random rnd = new Random();
    byte[] privKey1 = new byte[32];
    rnd.nextBytes(privKey1);

    mockNetwork.start().reportExceptions();
    Eth2DiscoveryServiceBuilder discoveryBuilder = new Eth2DiscoveryServiceBuilder();
    discoveryBuilder
        .privateKey(privKey1)
        .network(Optional.of(mockNetwork))
        .eventBus(eventBus)
        .networkInterface("127.0.0.1")
        .port(9001);
    Eth2DiscoveryService dm = discoveryBuilder.build();

    DiscoveryPeerSubscriberImpl sip = new DiscoveryPeerSubscriberImpl(mockNetwork);
    eventBus.register(sip);

    final String remoteHostEnr =
        "-IS4QJxZ43ITU3AsQxvwlkyzZvImNBH9CFu3yxMFWOK5rddgb0WjtIOBlPzs1JOlfi6YbM6Em3Ueu5EW-IdoPynMj4QBgmlkgnY0gmlwhKwSAAOJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCIys";
    NodeRecord remoteNodeRecord = NodeRecordFactory.DEFAULT.fromBase64(remoteHostEnr);
    NodeRecordInfo remoteNodeRecordInfo = NodeRecordInfo.createDefault(remoteNodeRecord);
    dm.getNodeTable().save(remoteNodeRecordInfo);
    InetAddress byAddress =
        InetAddress.getByAddress(((Bytes) remoteNodeRecord.get(IP_V4)).toArray());

    State state = (State) dm.start().get(10, TimeUnit.SECONDS);
    Assertions.assertEquals(
        State.RUNNING, state, "Failed to start discovery manager with mock network");

    verify(mockNetwork)
        .connect(
            "/ip4/"
                + byAddress.getHostAddress()
                + "/tcp/"
                + (int) remoteNodeRecord.get(UDP_V4)
                + "/p2p/"
                + Base58.INSTANCE.encode(remoteNodeRecord.getNodeId().toArray()));
  }

  @Test
  void nodeTableIntegrationTest() throws Exception {
    final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
    Eth2Network network1 = networkFactory.startNetwork();

    Random rnd = new Random();
    byte[] privKey1 = new byte[32];
    rnd.nextBytes(privKey1);

    Eth2DiscoveryServiceBuilder discoveryBuilder = new Eth2DiscoveryServiceBuilder();
    discoveryBuilder
        .privateKey(privKey1)
        .network(Optional.of(mockNetwork))
        .eventBus(eventBus)
        .networkInterface("127.0.0.1")
        .port(9001);
    Eth2DiscoveryService dm = discoveryBuilder.build();

    DiscoveryPeerSubscriberImpl sip = new DiscoveryPeerSubscriberImpl(network1);
    eventBus.register(sip);

    final String remoteHostEnr =
        "-IS4QJxZ43ITU3AsQxvwlkyzZvImNBH9CFu3yxMFWOK5rddgb0WjtIOBlPzs1JOlfi6YbM6Em3Ueu5EW-IdoPynMj4QBgmlkgnY0gmlwhKwSAAOJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCIys";
    NodeRecord remoteNodeRecord = NodeRecordFactory.DEFAULT.fromBase64(remoteHostEnr);
    NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(remoteNodeRecord);
    dm.getNodeTable().save(nodeRecordInfo);

    Assertions.assertTrue(
        dm.getNodeTable().getNode(nodeRecordInfo.getNode().getNodeId()).isPresent());

    networkFactory.stopAll();
  }
}
