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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;

import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager;
import tech.pegasys.teku.networking.p2p.connection.ReputationManager;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.util.async.SafeFuture;

class DiscoveryNetworkTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> p2pNetwork = mock(P2PNetwork.class);

  private final ReputationManager reputationManager = mock(ReputationManager.class);

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
    verifyNoInteractions(connectionManager);

    p2pStart.complete(null);
    verifyNoInteractions(connectionManager);

    discoveryStart.complete(null);
    verify(connectionManager).start();
    assertThat(started).isNotDone();

    connectionManagerStart.complete(null);
    assertThat(started).isCompleted();
  }

  @Test
  public void shouldStopConnectionManagerBeforeNetworkAndDiscovery() {
    final SafeFuture<Void> connectionStop = new SafeFuture<>();
    doReturn(new SafeFuture<Void>()).when(discoveryService).stop();
    doReturn(connectionStop).when(connectionManager).stop();

    discoveryNetwork.stop();

    verify(connectionManager).stop();
    verifyNoInteractions(p2pNetwork, discoveryService);

    connectionStop.complete(null);
    verify(p2pNetwork).stop();
    verify(discoveryService).stop();
  }

  @Test
  public void shouldStopNetworkAndDiscoveryWhenConnectionManagerStopFails() {
    final SafeFuture<Void> connectionStop = new SafeFuture<>();
    doReturn(new SafeFuture<Void>()).when(discoveryService).stop();
    doReturn(connectionStop).when(connectionManager).stop();

    discoveryNetwork.stop();

    verify(connectionManager).stop();
    verifyNoInteractions(p2pNetwork, discoveryService);

    connectionStop.completeExceptionally(new RuntimeException("Nope"));
    verify(p2pNetwork).stop();
    verify(discoveryService).stop();
  }

  @Test
  public void shouldReturnEnrFromDiscoveryService() {
    when(discoveryService.getEnr()).thenReturn(Optional.of("enr:-"));
    assertThat(discoveryNetwork.getEnr()).contains("enr:-");
  }

  @Test
  public void shouldNotEnableDiscoveryWhenDiscoveryIsDisabled() {
    final DiscoveryNetwork<Peer> network =
        DiscoveryNetwork.create(
            p2pNetwork,
            reputationManager,
            new NetworkConfig(
                null,
                "127.0.0.1",
                Optional.empty(),
                0,
                OptionalInt.empty(),
                Collections.emptyList(),
                false,
                Collections.emptyList(),
                new TargetPeerRange(20, 30)));
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
}
