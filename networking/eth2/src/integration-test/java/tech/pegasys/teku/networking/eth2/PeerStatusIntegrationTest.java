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

import com.google.common.eventbus.EventBus;
import java.time.Duration;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

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
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldExchangeStatusMessagesOnConnection() throws Exception {
    final RpcEncoding encoding = RpcEncoding.SSZ_SNAPPY;
    final EventBus eventBus2 = new EventBus();
    final RecentChainData recentChainData2 = MemoryOnlyRecentChainData.create(eventBus2);
    BeaconChainUtil.create(recentChainData2, VALIDATOR_KEYS).initializeStorage();

    final Eth2Network network1 =
        networkFactory
            .builder()
            .rpcEncoding(encoding)
            .recentChainData(recentChainData1)
            .startNetwork();
    final Eth2Network network2 =
        networkFactory
            .builder()
            .rpcEncoding(encoding)
            .eventBus(eventBus2)
            .recentChainData(recentChainData2)
            .startNetwork();

    waitFor(network1.connect(network1.createPeerAddress(network2.getNodeAddress())));
    waitFor(
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
    final RpcEncoding encoding = RpcEncoding.SSZ_SNAPPY;
    final Eth2Network network1 =
        networkFactory
            .builder()
            .rpcEncoding(encoding)
            .recentChainData(recentChainData1)
            .startNetwork();

    final EventBus eventBus2 = new EventBus();
    final RecentChainData recentChainData2 = MemoryOnlyRecentChainData.create(eventBus2);
    BeaconChainUtil.create(recentChainData2, VALIDATOR_KEYS).initializeStorage();
    final Eth2Network network2 =
        networkFactory
            .builder()
            .rpcEncoding(encoding)
            .eventBus(eventBus2)
            .recentChainData(recentChainData2)
            .peer(network1)
            .startNetwork();

    final Eth2Peer network2ViewOfPeer1 = network2.getPeer(network1.getNodeId()).orElseThrow();

    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());

    // Peer 1 advances
    beaconChainUtil1.createAndImportBlockAtSlot(10);

    final PeerStatus updatedStatusData = waitFor(network2ViewOfPeer1.sendStatus());
    assertStatusMatchesStorage(recentChainData1, updatedStatusData);
    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());
  }

  @Test
  public void shouldUpdatePeerStatusPeriodically() throws Exception {
    final RpcEncoding encoding = RpcEncoding.SSZ_SNAPPY;
    Duration statusUpdateInterval = Duration.ofSeconds(2);
    final Eth2Network network1 =
        networkFactory
            .builder()
            .rpcEncoding(encoding)
            .eth2StatusUpdateInterval(statusUpdateInterval)
            .recentChainData(recentChainData1)
            .startNetwork();

    final EventBus eventBus2 = new EventBus();
    final RecentChainData recentChainData2 = MemoryOnlyRecentChainData.create(eventBus2);
    BeaconChainUtil.create(recentChainData2, VALIDATOR_KEYS).initializeStorage();
    final Eth2Network network2 =
        networkFactory
            .builder()
            .rpcEncoding(encoding)
            .eventBus(eventBus2)
            .eth2StatusUpdateInterval(statusUpdateInterval)
            .recentChainData(recentChainData2)
            .peer(network1)
            .startNetwork();

    final Eth2Peer network2ViewOfPeer1 = network2.getPeer(network1.getNodeId()).orElseThrow();

    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());

    // Peer 1 advances
    beaconChainUtil1.createAndImportBlockAtSlot(10);

    waitFor(() -> assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus()));
  }

  private void assertStatusMatchesStorage(
      final RecentChainData storageClient, final PeerStatus status) {
    final BeaconState state = storageClient.getBestState().orElseThrow();
    assertStatus(
        status,
        storageClient.getHeadForkInfo().orElseThrow().getForkDigest(),
        state.getFinalized_checkpoint().getRoot(),
        state.getFinalized_checkpoint().getEpoch(),
        storageClient.getBestBlockRoot().orElseThrow(),
        storageClient.getHeadSlot());
  }

  private void assertStatus(
      final PeerStatus status,
      final Bytes4 forkDigest,
      final Bytes32 finalizedRoot,
      final UInt64 finalizedEpoch,
      final Bytes32 headRoot,
      final UInt64 headSlot) {
    assertThat(status.getForkDigest()).isEqualTo(forkDigest);
    assertThat(status.getFinalizedRoot()).isEqualTo(finalizedRoot);
    assertThat(status.getFinalizedEpoch()).isEqualTo(finalizedEpoch);
    assertThat(status.getHeadRoot()).isEqualTo(headRoot);
    assertThat(status.getHeadSlot()).isEqualTo(headSlot);
  }
}
