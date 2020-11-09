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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.StateStorageMode;

public class BeaconBlocksByRootIntegrationTest {
  protected final StorageSystem storageSystem1 =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  protected final StorageSystem storageSystem2 =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Peer peer1;
  private RecentChainData storageClient1;

  @BeforeEach
  public void setUp() throws Exception {
    final RpcEncoding rpcEncoding = getEncoding();

    storageSystem1.chainUpdater().initializeGenesis();
    storageSystem2.chainUpdater().initializeGenesis();

    storageClient1 = storageSystem1.recentChainData();
    final Eth2Network network1 =
        networkFactory
            .builder()
            .rpcEncoding(rpcEncoding)
            .eventBus(storageSystem1.eventBus())
            .recentChainData(storageClient1)
            .startNetwork();

    final Eth2Network network2 =
        networkFactory
            .builder()
            .rpcEncoding(rpcEncoding)
            .recentChainData(storageSystem2.recentChainData())
            .eventBus(storageSystem2.eventBus())
            .peer(network1)
            .startNetwork();

    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldSendEmptyResponseWhenNoBlocksAreAvailable() throws Exception {
    final List<SignedBeaconBlock> response = requestBlocks(singletonList(Bytes32.ZERO));
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldReturnSingleBlockWhenOnlyOneMatches() throws Exception {
    final SignedBeaconBlock block = addBlock();

    final List<SignedBeaconBlock> response =
        requestBlocks(singletonList(block.getMessage().hash_tree_root()));
    assertThat(response).containsExactly(block);
  }

  @Test
  public void requestBlocksByRootAfterPeerDisconnectedImmediately() throws RpcException {
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hash_tree_root();

    peer1.disconnectImmediately(Optional.empty(), false);
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer1.requestBlocksByRoot(List.of(blockHash), ResponseStreamListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlocksByRootAfterPeerDisconnected()
      throws RpcException, InterruptedException, ExecutionException, TimeoutException {
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hash_tree_root();

    Waiter.waitFor(peer1.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer1.requestBlocksByRoot(List.of(blockHash), ResponseStreamListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnectedImmediately() {
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hash_tree_root();

    peer1.disconnectImmediately(Optional.empty(), false);
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer1.requestBlockByRoot(blockHash);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnected()
      throws InterruptedException, ExecutionException, TimeoutException {
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hash_tree_root();

    Waiter.waitFor(peer1.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer1.requestBlockByRoot(blockHash);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void shouldReturnMultipleBlocksWhenAllRequestsMatch() throws Exception {
    final List<SignedBeaconBlock> blocks = asList(addBlock(), addBlock(), addBlock());
    final List<Bytes32> blockRoots =
        blocks.stream()
            .map(SignedBeaconBlock::getMessage)
            .map(BeaconBlock::hash_tree_root)
            .collect(toList());
    final List<SignedBeaconBlock> response = requestBlocks(blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  @Test
  public void shouldReturnMultipleLargeBlocksWhenAllRequestsMatch() throws Exception {
    final List<SignedBeaconBlock> blocks = largeBlockSequence(3);
    final List<Bytes32> blockRoots =
        blocks.stream().map(SignedBeaconBlock::getRoot).collect(toList());
    final List<SignedBeaconBlock> response = requestBlocks(blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  @Test
  public void shouldReturnMatchingBlocksWhenSomeRequestsDoNotMatch() throws Exception {
    final List<SignedBeaconBlock> blocks = asList(addBlock(), addBlock(), addBlock());

    // Real block roots interspersed with ones that don't match any blocks
    final List<Bytes32> blockRoots =
        blocks.stream()
            .map(SignedBeaconBlock::getMessage)
            .map(BeaconBlock::hash_tree_root)
            .flatMap(hash -> Stream.of(Bytes32.fromHexStringLenient("0x123456789"), hash))
            .collect(toList());

    final List<SignedBeaconBlock> response = requestBlocks(blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  @Test
  void requestBlockByRoot_shouldReturnEmptyWhenBlockIsNotKnown() throws Exception {
    final Optional<SignedBeaconBlock> result =
        waitFor(peer1.requestBlockByRoot(Bytes32.fromHexStringLenient("0x123456789")));
    assertThat(result).isEmpty();
  }

  private SignedBeaconBlock addBlock() {
    final SignedBlockAndState blockAndState = storageSystem1.chainUpdater().advanceChain();
    final StoreTransaction transaction = storageClient1.startStoreTransaction();
    transaction.putBlockAndState(blockAndState);
    assertThat(transaction.commit()).isCompleted();
    return blockAndState.getBlock();
  }

  private List<SignedBeaconBlock> largeBlockSequence(final int count) {
    DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final SignedBeaconBlock parent =
        storageSystem1.chainBuilder().getLatestBlockAndState().getBlock();
    List<SignedBlockAndState> newBlocks =
        dataStructureUtil.randomSignedBlockAndStateSequence(parent, count, true);
    newBlocks.forEach(storageSystem1.chainUpdater()::saveBlock);

    return newBlocks.stream().map(SignedBlockAndState::getBlock).collect(Collectors.toList());
  }

  private List<SignedBeaconBlock> requestBlocks(final List<Bytes32> blockRoots)
      throws InterruptedException, java.util.concurrent.ExecutionException,
          java.util.concurrent.TimeoutException, RpcException {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(peer1.requestBlocksByRoot(blockRoots, ResponseStreamListener.from(blocks::add)));
    return blocks;
  }

  private RpcEncoding getEncoding() {
    return RpcEncoding.SSZ_SNAPPY;
  }
}
