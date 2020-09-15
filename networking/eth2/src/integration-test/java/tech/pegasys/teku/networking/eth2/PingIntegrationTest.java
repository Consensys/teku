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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManagerAccess;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.util.config.Constants;

public class PingIntegrationTest {
  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Network network1;
  private Eth2Network network2;
  private Eth2Peer peer1;
  private Eth2Peer peer2;

  public void setUp(Duration pingInterval) throws Exception {
    network1 = networkFactory.builder().eth2RpcPingInterval(pingInterval).startNetwork();
    network2 =
        networkFactory.builder().eth2RpcPingInterval(pingInterval).peer(network1).startNetwork();
    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
    peer2 = network1.getPeer(network2.getNodeId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void testPingSimple() throws Exception {
    setUp(Duration.ofDays(1));

    peer1.sendPing().get(10, TimeUnit.SECONDS);
    // response received in 10 seconds
  }

  @Test
  public void testPingUpdatesMetadata() throws Exception {
    setUp(Duration.ofDays(1));

    Optional<Bitvector> attNets1_0 = peer1.getRemoteAttestationSubnets();
    Optional<Bitvector> attNets2_0 = peer2.getRemoteAttestationSubnets();

    assertThat(attNets1_0.isEmpty() || attNets1_0.get().getBitCount() == 0).isTrue();
    assertThat(attNets2_0.isEmpty() || attNets2_0.get().getBitCount() == 0).isTrue();

    MetadataMessage md1 = peer1.requestMetadata().get(10, TimeUnit.SECONDS);
    MetadataMessage md2 = peer1.requestMetadata().get(10, TimeUnit.SECONDS);

    UInt64 ping1_0 = peer1.sendPing().get(10, TimeUnit.SECONDS);
    UInt64 ping2_0 = peer2.sendPing().get(10, TimeUnit.SECONDS);

    assertThat(ping1_0).isEqualTo(md1.getSeqNumber());
    assertThat(ping2_0).isEqualTo(md2.getSeqNumber());

    UInt64 ping1_1 = peer1.sendPing().get(10, TimeUnit.SECONDS);
    UInt64 ping2_1 = peer2.sendPing().get(10, TimeUnit.SECONDS);

    assertThat(ping1_1).isEqualTo(md1.getSeqNumber());
    assertThat(ping2_1).isEqualTo(md2.getSeqNumber());

    network1.setLongTermAttestationSubnetSubscriptions(List.of(0, 1, 8));
    Thread.sleep(100);
    UInt64 ping1_2 = peer1.sendPing().get(10, TimeUnit.SECONDS);
    assertThat(ping1_2).isGreaterThan(ping1_1);

    Bitvector expectedBitvector1 = new Bitvector(Constants.ATTESTATION_SUBNET_COUNT);
    expectedBitvector1.setBits(0, 1, 8);

    waitFor(() -> assertThat(peer1.getRemoteAttestationSubnets()).contains(expectedBitvector1));
    waitFor(() -> assertThat(peer2.getRemoteAttestationSubnets()).isNotEmpty());

    network1.setLongTermAttestationSubnetSubscriptions(List.of(2, 4));
    Thread.sleep(100);
    UInt64 ping2_2 = peer2.sendPing().get(10, TimeUnit.SECONDS);
    assertThat(ping2_2).isEqualTo(ping2_1);

    Bitvector expectedBitvector2 = new Bitvector(Constants.ATTESTATION_SUBNET_COUNT);
    expectedBitvector2.setBits(2, 4);

    waitFor(() -> assertThat(peer1.getRemoteAttestationSubnets()).contains(expectedBitvector2));
  }

  @Test
  public void testPingPeriodically() throws Exception {
    setUp(Duration.ofMillis(100));

    network1.setLongTermAttestationSubnetSubscriptions(List.of(0, 1, 8));
    Bitvector expectedBitvector1 = new Bitvector(Constants.ATTESTATION_SUBNET_COUNT);
    expectedBitvector1.setBits(0, 1, 8);

    // detecting that ping was automatically sent via updated att subnets of the remote peer
    waitFor(() -> assertThat(peer1.getRemoteAttestationSubnets()).contains(expectedBitvector1));
  }

  @Test
  public void testManualPingTimeout() throws Exception {
    // sending PING manually to check eth2RpcOutstandingPingThreshold works correctly
    Duration pingPeriod = Duration.ofDays(1);
    network1 =
        networkFactory
            .builder()
            .eth2RpcPingInterval(pingPeriod)
            .eth2RpcOutstandingPingThreshold(2)
            // remove PING method
            .rpcMethodsModifier(m -> Stream.of(m).filter(t -> !t.getId().contains("/ping")))
            .startNetwork();
    network2 =
        networkFactory
            .builder()
            .eth2RpcPingInterval(pingPeriod)
            .eth2RpcOutstandingPingThreshold(2)
            .peer(network1)
            .startNetwork();

    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
    peer2 = network1.getPeer(network2.getNodeId()).orElseThrow();

    Eth2PeerManagerAccess.invokeSendPeriodicPing(getPeerManager(network2), peer1);
    assertThat(peer1.isConnected()).isTrue();
    Eth2PeerManagerAccess.invokeSendPeriodicPing(getPeerManager(network2), peer1);
    assertThat(peer1.isConnected()).isTrue();
    // the 3rd ping attempt should disconnect the peer since there are 2 unanswered PING requests
    Eth2PeerManagerAccess.invokeSendPeriodicPing(getPeerManager(network2), peer1);
    waitFor(() -> assertThat(peer1.isConnected()).isFalse());
  }

  private Eth2PeerManager getPeerManager(Eth2Network eth2Network) {
    return ((ActiveEth2Network) eth2Network).getPeerManager();
  }
}
