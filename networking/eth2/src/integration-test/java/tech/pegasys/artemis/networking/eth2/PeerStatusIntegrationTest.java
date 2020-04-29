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

package tech.pegasys.artemis.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.BLSKeyGenerator;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.PeerStatus;
import tech.pegasys.artemis.ssz.SSZTypes.Bytes4;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.Waiter;

public class PeerStatusIntegrationTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(1);
  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private final RecentChainData recentChainData1 = MemoryOnlyRecentChainData.create(new EventBus());
  private final BeaconChainUtil beaconChainUtil1 =
      BeaconChainUtil.create(recentChainData1, VALIDATOR_KEYS);

  @BeforeEach
  public void setUp() {
    beaconChainUtil1.initializeStorage();
  }

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldExchangeStatusMessagesOnConnection() throws Exception {
    final EventBus eventBus2 = new EventBus();
    final RecentChainData recentChainData2 = MemoryOnlyRecentChainData.create(eventBus2);
    BeaconChainUtil.create(recentChainData2, VALIDATOR_KEYS).initializeStorage();

    final Eth2Network network1 =
        networkFactory.builder().recentChainData(recentChainData1).startNetwork();
    final Eth2Network network2 =
        networkFactory
            .builder()
            .eventBus(eventBus2)
            .recentChainData(recentChainData2)
            .startNetwork();

    Waiter.waitFor(network1.connect(network1.createPeerAddress(network2.getNodeAddress())));
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    final Eth2Peer network2ViewOfPeer1 = network2.getPeer(network1.getNodeId()).orElseThrow();

    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());

    final Eth2Peer network1ViewOfPeer2 = network1.getPeer(network2.getNodeId()).orElseThrow();
    assertStatusMatchesStorage(recentChainData2, network1ViewOfPeer2.getStatus());
  }

  @Test
  public void shouldUpdatePeerStatus() throws Exception {
    final Eth2Network network1 =
        networkFactory.builder().recentChainData(recentChainData1).startNetwork();

    final EventBus eventBus2 = new EventBus();
    final RecentChainData storageClient2 = MemoryOnlyRecentChainData.create(eventBus2);
    BeaconChainUtil.create(storageClient2, VALIDATOR_KEYS).initializeStorage();
    final Eth2Network network2 =
        networkFactory
            .builder()
            .eventBus(eventBus2)
            .recentChainData(storageClient2)
            .peer(network1)
            .startNetwork();

    final Eth2Peer network2ViewOfPeer1 = network2.getPeer(network1.getNodeId()).orElseThrow();

    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());

    // Peer 1 advances
    beaconChainUtil1.createAndImportBlockAtSlot(10);

    final PeerStatus updatedStatusData = Waiter.waitFor(network2ViewOfPeer1.sendStatus());
    assertStatusMatchesStorage(recentChainData1, updatedStatusData);
    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());
  }

  private void assertStatusMatchesStorage(
      final RecentChainData storageClient, final PeerStatus status) {
    final Store network2Store = storageClient.getStore();
    assertStatus(
        status,
        storageClient.getCurrentForkInfo().orElseThrow().getForkDigest(),
        network2Store.getFinalizedCheckpoint().getRoot(),
        network2Store.getFinalizedCheckpoint().getEpoch(),
        storageClient.getBestBlockRoot().orElseThrow(),
        storageClient.getBestSlot());
  }

  private void assertStatus(
      final PeerStatus status,
      final Bytes4 forkDigest,
      final Bytes32 finalizedRoot,
      final UnsignedLong finalizedEpoch,
      final Bytes32 headRoot,
      final UnsignedLong headSlot) {
    assertThat(status.getForkDigest()).isEqualTo(forkDigest);
    assertThat(status.getFinalizedRoot()).isEqualTo(finalizedRoot);
    assertThat(status.getFinalizedEpoch()).isEqualTo(finalizedEpoch);
    assertThat(status.getHeadRoot()).isEqualTo(headRoot);
    assertThat(status.getHeadSlot()).isEqualTo(headSlot);
  }
}
