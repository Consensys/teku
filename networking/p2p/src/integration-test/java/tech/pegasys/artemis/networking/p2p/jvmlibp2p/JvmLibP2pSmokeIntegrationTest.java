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
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.NetworkFactory;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.Waiter;

public class JvmLibP2pSmokeIntegrationTest {

  private final NetworkFactory networkFactory = new NetworkFactory();

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldExchangeStatusMessagesOnConnection() throws Exception {
    final EventBus eventBus2 = new EventBus();
    final ChainStorageClient storageClient2 =
        StartupUtil.initChainStorageClient(eventBus2, 0, null, 0);
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
    assertThat(network2ViewOfPeer1.getStatus().getHeadForkVersion()).isEqualTo(Fork.VERSION_ZERO);
    assertThat(network2ViewOfPeer1.getStatus().getFinalizedRoot()).isEqualTo(Bytes32.ZERO);
    assertThat(network2ViewOfPeer1.getStatus().getFinalizedEpoch()).isEqualTo(UnsignedLong.ZERO);
    assertThat(network2ViewOfPeer1.getStatus().getHeadRoot()).isEqualTo(Bytes32.ZERO);
    assertThat(network2ViewOfPeer1.getStatus().getHeadSlot()).isEqualTo(UnsignedLong.ZERO);

    final Store network2Store = storageClient2.getStore();
    final Peer network1ViewOfPeer2 =
        network1.getPeerManager().getAvailablePeer(network2.getPeerId()).orElseThrow();
    assertThat(network1ViewOfPeer2.getStatus().getHeadForkVersion())
        .isEqualTo(storageClient2.getBestBlockRootState().getFork().getCurrent_version());
    assertThat(network1ViewOfPeer2.getStatus().getFinalizedRoot())
        .isEqualTo(network2Store.getFinalizedCheckpoint().getRoot());
    assertThat(network1ViewOfPeer2.getStatus().getFinalizedEpoch())
        .isEqualTo(network2Store.getFinalizedCheckpoint().getEpoch());
    assertThat(network1ViewOfPeer2.getStatus().getHeadRoot())
        .isEqualTo(storageClient2.getBestBlockRoot());
    assertThat(network1ViewOfPeer2.getStatus().getHeadSlot())
        .isEqualTo(storageClient2.getBestSlot());
  }

  @Test
  @Disabled("Chain storage is not configured so HELLO messages cause immediate disconnects")
  public void shouldGossipBlocks() throws Exception {
    final EventBus eventBus1 = new EventBus();
    final EventBus eventBus2 = mock(EventBus.class);

    final JvmLibP2PNetwork network1 = networkFactory.startNetwork(eventBus1);
    networkFactory.startNetwork(eventBus2, network1);

    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(100, 100);
    eventBus1.post(block);

    Waiter.waitFor(() -> verify(eventBus2).post(refEq(block)));
  }
}
