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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.network.p2p.jvmlibp2p.ChainStorageClientFactory.createInitedStorageClient;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.NetworkFactory;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Peer.StatusData;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.Waiter;

public class PeerStatusIntegrationTest {

  private final NetworkFactory networkFactory = new NetworkFactory();

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldExchangeStatusMessagesOnConnection() throws Exception {
    final EventBus eventBus2 = new EventBus();
    final ChainStorageClient storageClient2 = createInitedStorageClient(eventBus2);
    final JvmLibP2PNetwork network1 = networkFactory.startNetwork();
    final JvmLibP2PNetwork network2 = networkFactory.startNetwork(eventBus2, storageClient2);

    network1.connect(network2.getPeerAddress());
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerManager().getAvailablePeerCount()).isEqualTo(1);
          assertThat(network2.getPeerManager().getAvailablePeerCount()).isEqualTo(1);
        });

    final Peer network2ViewOfPeer1 =
        network2.getPeerManager().getAvailablePeer(network1.getPeerId()).orElseThrow();
    assertStatus(
        network2ViewOfPeer1.getStatus(),
        Fork.VERSION_ZERO,
        Bytes32.ZERO,
        UnsignedLong.ZERO,
        Bytes32.ZERO,
        UnsignedLong.ZERO);

    final Peer network1ViewOfPeer2 =
        network1.getPeerManager().getAvailablePeer(network2.getPeerId()).orElseThrow();
    assertStatusMatchesStorage(storageClient2, network1ViewOfPeer2.getStatus());
  }

  @Test
  public void shouldUpdatePeerStatus() throws Exception {
    final EventBus eventBus1 = new EventBus();
    final ChainStorageClient storageClient1 = new ChainStorageClient(eventBus1);
    final JvmLibP2PNetwork network1 = networkFactory.startNetwork(eventBus1, storageClient1);

    final EventBus eventBus2 = new EventBus();
    final ChainStorageClient storageClient2 = createInitedStorageClient(eventBus2);
    final JvmLibP2PNetwork network2 =
        networkFactory.startNetwork(eventBus2, storageClient2, network1);

    final Peer network2ViewOfPeer1 =
        network2.getPeerManager().getAvailablePeer(network1.getPeerId()).orElseThrow();

    assertStatus(
        network2ViewOfPeer1.getStatus(),
        Fork.VERSION_ZERO,
        Bytes32.ZERO,
        UnsignedLong.ZERO,
        Bytes32.ZERO,
        UnsignedLong.ZERO);

    // Peer 1 goes through genesis event.
    StartupUtil.setupInitialState(storageClient1, 0, null, 0);

    final StatusData updatedStatusData = Waiter.waitFor(network2ViewOfPeer1.sendStatus());
    assertStatusMatchesStorage(storageClient1, updatedStatusData);
    assertStatusMatchesStorage(storageClient1, network2ViewOfPeer1.getStatus());
  }

  private void assertStatus(
      final StatusData status,
      final Bytes4 versionZero,
      final Bytes32 zero,
      final UnsignedLong zero2,
      final Bytes32 zero3,
      final UnsignedLong zero4) {
    assertThat(status.getHeadForkVersion()).isEqualTo(versionZero);
    assertThat(status.getFinalizedRoot()).isEqualTo(zero);
    assertThat(status.getFinalizedEpoch()).isEqualTo(zero2);
    assertThat(status.getHeadRoot()).isEqualTo(zero3);
    assertThat(status.getHeadSlot()).isEqualTo(zero4);
  }

  private void assertStatusMatchesStorage(
      final ChainStorageClient storageClient, final StatusData status) {
    final Store network2Store = storageClient.getStore();
    assertStatus(
        status,
        storageClient.getBestBlockRootState().getFork().getCurrent_version(),
        network2Store.getFinalizedCheckpoint().getRoot(),
        network2Store.getFinalizedCheckpoint().getEpoch(),
        storageClient.getBestBlockRoot(),
        storageClient.getBestSlot());
  }
}
