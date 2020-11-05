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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class BeaconBlocksByRangeIntegrationTest {

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
  private Eth2Peer peer;

  @BeforeEach
  public void setUp() throws Exception {
    final StorageSystem storageSystem2 = InMemoryStorageSystemBuilder.buildDefault();
    final RpcEncoding rpcEncoding = getEncoding();
    storageSystem.chainUpdater().initializeGenesis();
    storageSystem2.chainUpdater().initializeGenesis();

    final Eth2Network network1 =
        networkFactory
            .builder()
            .rpcEncoding(rpcEncoding)
            .eventBus(storageSystem.eventBus())
            .recentChainData(storageSystem.recentChainData())
            .historicalChainData(storageSystem.chainStorage())
            .startNetwork();

    final Eth2Network network2 =
        networkFactory
            .builder()
            .rpcEncoding(rpcEncoding)
            .peer(network1)
            .recentChainData(storageSystem2.recentChainData())
            .historicalChainData(storageSystem2.chainStorage())
            .startNetwork();
    peer = network2.getPeer(network1.getNodeId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldSendEmptyResponsePreGenesisEvent() throws Exception {
    final List<SignedBeaconBlock> response = requestBlocks();
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenNoBlocksAreAvailable() throws Exception {
    final List<SignedBeaconBlock> response = requestBlocks();
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldRespondWithBlocksFromCanonicalChain() throws Exception {
    final SignedBlockAndState block1 = storageSystem.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().advanceChain();
    storageSystem.chainUpdater().updateBestBlock(block2);

    final List<SignedBeaconBlock> response = requestBlocks();
    assertThat(response).containsExactly(block1.getBlock(), block2.getBlock());
  }

  @Test
  public void requestBlocksByRangeAfterPeerDisconnectedImmediately() throws Exception {
    // Setup chain
    storageSystem.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().advanceChain();
    storageSystem.chainUpdater().updateBestBlock(block2);

    peer.disconnectImmediately(Optional.empty(), false);
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), UInt64.ONE, ResponseStreamListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlocksByRangeAfterPeerDisconnected() throws Exception {
    // Setup chain
    storageSystem.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().advanceChain();
    storageSystem.chainUpdater().updateBestBlock(block2);

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), UInt64.ONE, ResponseStreamListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlockBySlotAfterPeerDisconnectedImmediately() throws Exception {
    // Setup chain
    storageSystem.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().advanceChain();
    storageSystem.chainUpdater().updateBestBlock(block2);

    peer.disconnectImmediately(Optional.empty(), false);
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer.requestBlockBySlot(UInt64.ONE);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnected() throws Exception {
    // Setup chain
    storageSystem.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().advanceChain();
    storageSystem.chainUpdater().updateBestBlock(block2);

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer.requestBlockBySlot(UInt64.ONE);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  void requestBlockBySlot_shouldReturnEmptyWhenBlockIsNotKnown() throws Exception {
    final Optional<SignedBeaconBlock> result =
        waitFor(peer.requestBlockBySlot(UInt64.valueOf(49382982)));
    assertThat(result).isEmpty();
  }

  private List<SignedBeaconBlock> requestBlocks()
      throws InterruptedException, java.util.concurrent.ExecutionException,
          java.util.concurrent.TimeoutException {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), UInt64.ONE, ResponseStreamListener.from(blocks::add)));
    return blocks;
  }

  protected RpcEncoding getEncoding() {
    return RpcEncoding.SSZ_SNAPPY;
  }
}
