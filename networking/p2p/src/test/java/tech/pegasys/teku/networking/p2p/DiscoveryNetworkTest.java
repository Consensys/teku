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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_fork_digest;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.util.config.Constants.GENESIS_FORK_VERSION;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.network.p2p.peer.SimplePeerSelectionStrategy;
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.storage.store.MemKeyValueStore;

class DiscoveryNetworkTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> p2pNetwork = mock(P2PNetwork.class);

  private final DiscoveryService discoveryService = mock(DiscoveryService.class);
  private final ConnectionManager connectionManager = mock(ConnectionManager.class);

  private final DiscoveryNetwork<Peer> discoveryNetwork =
      new DiscoveryNetwork<>(p2pNetwork, discoveryService, connectionManager);

  @Test
  public void shouldStartConnectionManagerAfterP2pAndDiscoveryStarted() {
    final SafeFuture<Void> p2pStart = new SafeFuture<>();
    final SafeFuture<Void> discoveryStart = new SafeFuture<>();
    final SafeFuture<Object> connectionManagerStart = new SafeFuture<>();
    doReturn(p2pStart).when(p2pNetwork).start();
    doReturn(discoveryStart).when(discoveryService).start();
    doReturn(connectionManagerStart).when(connectionManager).start();

    final SafeFuture<?> started = discoveryNetwork.start();

    verify(p2pNetwork).start();
    verify(discoveryService).start();
    verify(connectionManager, never()).start();

    p2pStart.complete(null);
    verify(connectionManager, never()).start();

    discoveryStart.complete(null);
    verify(connectionManager).start();
    assertThat(started).isNotDone();

    connectionManagerStart.complete(null);
    assertThat(started).isCompleted();
  }

  @Test
  public void shouldReturnEnrFromDiscoveryService() {
    when(discoveryService.getEnr()).thenReturn(Optional.of("enr:-"));
    assertThat(discoveryNetwork.getEnr()).contains("enr:-");
  }

  @Test
  @SuppressWarnings({"FutureReturnValueIgnored"})
  public void shouldStopConnectionManagerBeforeNetworkAndDiscovery() {
    final SafeFuture<Void> connectionStop = new SafeFuture<>();
    doReturn(new SafeFuture<Void>()).when(discoveryService).stop();
    doReturn(connectionStop).when(connectionManager).stop();

    Waiter.waitFor(discoveryNetwork::stop);

    verify(connectionManager).stop();
    verify(discoveryService).updateCustomENRField(any(), any());
    verify(discoveryService).getEnr();
    verifyNoMoreInteractions(discoveryService);
    verifyNoInteractions(p2pNetwork);

    connectionStop.complete(null);
    verify(p2pNetwork).stop();
    verify(discoveryService).stop();
  }

  @Test
  @SuppressWarnings({"FutureReturnValueIgnored"})
  public void shouldStopNetworkAndDiscoveryWhenConnectionManagerStopFails() {
    final SafeFuture<Void> connectionStop = new SafeFuture<>();
    doReturn(new SafeFuture<Void>()).when(discoveryService).stop();
    doReturn(connectionStop).when(connectionManager).stop();

    Waiter.waitFor(discoveryNetwork::stop);

    verify(connectionManager).stop();
    verify(discoveryService).updateCustomENRField(any(), any());
    verify(discoveryService).getEnr();
    verifyNoMoreInteractions(discoveryService);
    verifyNoInteractions(p2pNetwork);

    connectionStop.completeExceptionally(new RuntimeException("Nope"));
    verify(p2pNetwork).stop();
    verify(discoveryService).stop();
  }

  @Test
  public void shouldNotEnableDiscoveryWhenDiscoveryIsDisabled() {
    final NetworkConfig networkConfig =
        new NetworkConfig(
            null,
            "127.0.0.1",
            Optional.empty(),
            0,
            OptionalInt.empty(),
            Collections.emptyList(),
            false,
            Collections.emptyList(),
            new TargetPeerRange(20, 30, 0),
            2);
    final DiscoveryNetwork<Peer> network =
        DiscoveryNetwork.create(
            new NoOpMetricsSystem(),
            DelayedExecutorAsyncRunner.create(),
            new MemKeyValueStore<>(),
            p2pNetwork,
            new SimplePeerSelectionStrategy(networkConfig.getTargetPeerRange()),
            networkConfig);
    assertThat(network.getEnr()).isEmpty();
  }

  @Test
  public void setForkInfo_noFutureForkScheduled() {
    final ForkInfo currentForkInfo = dataStructureUtil.randomForkInfo();
    discoveryNetwork.setForkInfo(currentForkInfo, Optional.empty());

    final EnrForkId expectedEnrForkId =
        new EnrForkId(
            currentForkInfo.getForkDigest(),
            currentForkInfo.getFork().getCurrent_version(),
            FAR_FUTURE_EPOCH);
    verify(discoveryService)
        .updateCustomENRField("eth2", SimpleOffsetSerializer.serialize(expectedEnrForkId));
  }

  @Test
  public void setForkInfo_futureForkScheduled() {
    final ForkInfo currentForkInfo = dataStructureUtil.randomForkInfo();
    final Fork nextFork = dataStructureUtil.randomFork();
    discoveryNetwork.setForkInfo(currentForkInfo, Optional.of(nextFork));

    final EnrForkId expectedEnrForkId =
        new EnrForkId(
            currentForkInfo.getForkDigest(), nextFork.getCurrent_version(), nextFork.getEpoch());
    verify(discoveryService)
        .updateCustomENRField("eth2", SimpleOffsetSerializer.serialize(expectedEnrForkId));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void setForkInfoShouldAddPredicateToConnectionManager() {
    final ForkInfo currentForkInfo = dataStructureUtil.randomForkInfo();
    discoveryNetwork.setForkInfo(currentForkInfo, Optional.empty());

    final EnrForkId expectedEnrForkId =
        new EnrForkId(
            currentForkInfo.getForkDigest(),
            currentForkInfo.getFork().getCurrent_version(),
            FAR_FUTURE_EPOCH);
    Bytes encodedForkId = SimpleOffsetSerializer.serialize(expectedEnrForkId);
    verify(discoveryService).updateCustomENRField("eth2", encodedForkId);
    ArgumentCaptor<Predicate<DiscoveryPeer>> peerPredicateArgumentCaptor =
        ArgumentCaptor.forClass(Predicate.class);
    verify(connectionManager).addPeerPredicate(peerPredicateArgumentCaptor.capture());

    DiscoveryPeer peer1 = createDiscoveryPeer(Optional.of(expectedEnrForkId));
    assertThat(peerPredicateArgumentCaptor.getValue().test(peer1)).isTrue();

    final EnrForkId newEnrForkId1 =
        new EnrForkId(
            currentForkInfo.getForkDigest(), Bytes4.fromHexString("0xdeadbeef"), UInt64.ZERO);
    DiscoveryPeer peer2 = createDiscoveryPeer(Optional.of(newEnrForkId1));
    assertThat(peerPredicateArgumentCaptor.getValue().test(peer2)).isTrue();

    final EnrForkId newEnrForkId2 =
        new EnrForkId(
            Bytes4.fromHexString("0xdeadbeef"), Bytes4.fromHexString("0xdeadbeef"), UInt64.ZERO);
    DiscoveryPeer peer3 = createDiscoveryPeer(Optional.of(newEnrForkId2));
    assertThat(peerPredicateArgumentCaptor.getValue().test(peer3)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldNotConnectToPeerWithNoEnrForkId() {
    final ForkInfo currentForkInfo = dataStructureUtil.randomForkInfo();
    discoveryNetwork.setForkInfo(currentForkInfo, Optional.empty());

    final EnrForkId expectedEnrForkId =
        new EnrForkId(
            currentForkInfo.getForkDigest(),
            currentForkInfo.getFork().getCurrent_version(),
            FAR_FUTURE_EPOCH);
    Bytes encodedForkId = SimpleOffsetSerializer.serialize(expectedEnrForkId);
    verify(discoveryService).updateCustomENRField("eth2", encodedForkId);
    ArgumentCaptor<Predicate<DiscoveryPeer>> peerPredicateArgumentCaptor =
        ArgumentCaptor.forClass(Predicate.class);
    verify(connectionManager).addPeerPredicate(peerPredicateArgumentCaptor.capture());

    DiscoveryPeer peer1 = createDiscoveryPeer(Optional.empty());
    assertThat(peerPredicateArgumentCaptor.getValue().test(peer1)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldNotConnectToPeersWhenNodeHasNoEnrForkId() {
    final ForkInfo currentForkInfo = dataStructureUtil.randomForkInfo();

    final EnrForkId enrForkId =
        new EnrForkId(
            currentForkInfo.getForkDigest(),
            currentForkInfo.getFork().getCurrent_version(),
            FAR_FUTURE_EPOCH);
    ArgumentCaptor<Predicate<DiscoveryPeer>> peerPredicateArgumentCaptor =
        ArgumentCaptor.forClass(Predicate.class);
    verify(connectionManager).addPeerPredicate(peerPredicateArgumentCaptor.capture());

    DiscoveryPeer peer1 = createDiscoveryPeer(Optional.of(enrForkId));
    assertThat(peerPredicateArgumentCaptor.getValue().test(peer1)).isFalse();
  }

  @Test
  public void setForkInfoAtInitialization() {
    final EnrForkId enrForkId =
        new EnrForkId(
            compute_fork_digest(GENESIS_FORK_VERSION, Bytes32.ZERO),
            GENESIS_FORK_VERSION,
            FAR_FUTURE_EPOCH);
    verify(discoveryService)
        .updateCustomENRField("eth2", SimpleOffsetSerializer.serialize(enrForkId));
  }

  public DiscoveryPeer createDiscoveryPeer(Optional<EnrForkId> maybeForkId) {
    return new DiscoveryPeer(
        BLSPublicKey.empty().toSSZBytes(),
        InetSocketAddress.createUnresolved("yo", 9999),
        maybeForkId,
        new Bitvector(ATTESTATION_SUBNET_COUNT));
  }
}
