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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.artemis.util.Waiter.waitFor;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.p2p.peer.DisconnectRequestHandler.DisconnectReason;
import tech.pegasys.artemis.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BeaconBlocksByRangeIntegrationTest {

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Peer peer1;
  private RecentChainData storageClient1;
  private BeaconChainUtil beaconChainUtil;

  @BeforeEach
  public void setUp() throws Exception {
    final EventBus eventBus1 = new EventBus();
    storageClient1 = MemoryOnlyRecentChainData.create(eventBus1);
    final Eth2Network network1 =
        networkFactory.builder().eventBus(eventBus1).recentChainData(storageClient1).startNetwork();

    final Eth2Network network2 = networkFactory.builder().peer(network1).startNetwork();
    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
    beaconChainUtil = BeaconChainUtil.create(1, storageClient1);
  }

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldSendEmptyResponsePreGenesisEvent() throws Exception {
    final List<SignedBeaconBlock> response = requestBlocks();
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenNoBlocksAreAvailable() throws Exception {
    beaconChainUtil.initializeStorage();
    final List<SignedBeaconBlock> response = requestBlocks();
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldRespondWithBlocksFromCanonicalChain() throws Exception {
    beaconChainUtil.initializeStorage();

    final SignedBeaconBlock block1 = beaconChainUtil.createAndImportBlockAtSlot(1);
    final Bytes32 block1Root = block1.getMessage().hash_tree_root();
    storageClient1.updateBestBlock(block1Root, block1.getSlot());

    final SignedBeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2);
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    storageClient1.updateBestBlock(block2Root, block2.getSlot());

    final List<SignedBeaconBlock> response = requestBlocks();
    assertThat(response).containsExactly(block1, block2);
  }

  @Test
  public void requestBlocksByRangeAfterPeerDisconnectedImmediately() throws Exception {
    // Setup chain
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(1);
    final SignedBeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2);
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    storageClient1.updateBestBlock(block2Root, block2.getSlot());

    peer1.disconnectImmediately();
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer1.requestBlocksByRange(
            UnsignedLong.ONE, UnsignedLong.valueOf(10), UnsignedLong.ONE, blocks::add);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlocksByRangeAfterPeerDisconnected() throws Exception {
    // Setup chain
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(1);
    final SignedBeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2);
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    storageClient1.updateBestBlock(block2Root, block2.getSlot());

    peer1.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS);
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer1.requestBlocksByRange(
            UnsignedLong.ONE, UnsignedLong.valueOf(10), UnsignedLong.ONE, blocks::add);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlockBySlotAfterPeerDisconnectedImmediately() throws Exception {
    // Setup chain
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(1);
    final SignedBeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2);
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    storageClient1.updateBestBlock(block2Root, block2.getSlot());

    peer1.disconnectImmediately();
    final SafeFuture<SignedBeaconBlock> res = peer1.requestBlockBySlot(UnsignedLong.ONE);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnected() throws Exception {
    // Setup chain
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(1);
    final SignedBeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2);
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    storageClient1.updateBestBlock(block2Root, block2.getSlot());

    peer1.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS);
    final SafeFuture<SignedBeaconBlock> res = peer1.requestBlockBySlot(UnsignedLong.ONE);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  private List<SignedBeaconBlock> requestBlocks()
      throws InterruptedException, java.util.concurrent.ExecutionException,
          java.util.concurrent.TimeoutException {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer1.requestBlocksByRange(
            UnsignedLong.ONE, UnsignedLong.valueOf(10), UnsignedLong.ONE, blocks::add));
    return blocks;
  }
}
