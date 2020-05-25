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
import static tech.pegasys.teku.util.Waiter.waitFor;

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.peer.DisconnectRequestHandler.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.Store.Transaction;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.async.SafeFuture;

public abstract class BeaconBlocksByRootIntegrationTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Peer peer1;
  private RecentChainData storageClient1;

  @BeforeEach
  public void setUp() throws Exception {
    final EventBus eventBus1 = new EventBus();
    final RpcEncoding rpcEncoding = getEncoding();

    storageClient1 = MemoryOnlyRecentChainData.create(eventBus1);
    BeaconChainUtil.create(0, storageClient1).initializeStorage();
    final Eth2Network network1 =
        networkFactory
            .builder()
            .rpcEncoding(rpcEncoding)
            .eventBus(eventBus1)
            .recentChainData(storageClient1)
            .startNetwork();
    final Eth2Network network2 =
        networkFactory.builder().rpcEncoding(rpcEncoding).peer(network1).startNetwork();
    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
  }

  protected abstract RpcEncoding getEncoding();

  @AfterEach
  public void tearDown() {
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
  public void requestBlocksByRootAfterPeerDisconnectedImmediately() {
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hash_tree_root();

    peer1.disconnectImmediately();
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res = peer1.requestBlocksByRoot(List.of(blockHash), blocks::add);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlocksByRootAfterPeerDisconnected() {
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hash_tree_root();

    peer1.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS);
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res = peer1.requestBlocksByRoot(List.of(blockHash), blocks::add);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnectedImmediately() {
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hash_tree_root();

    peer1.disconnectImmediately();
    final SafeFuture<SignedBeaconBlock> res = peer1.requestBlockByRoot(blockHash);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnected() {
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hash_tree_root();

    peer1.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS);
    final SafeFuture<SignedBeaconBlock> res = peer1.requestBlockByRoot(blockHash);

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
    final List<SignedBeaconBlock> blocks = asList(addBlock(true), addBlock(true), addBlock(true));
    final List<Bytes32> blockRoots =
        blocks.stream()
            .map(SignedBeaconBlock::getMessage)
            .map(BeaconBlock::hash_tree_root)
            .collect(toList());
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

  private SignedBeaconBlock addBlock() {
    return addBlock(false);
  }

  private SignedBeaconBlock addBlock(boolean full) {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(1, dataStructureUtil.randomBytes32(), full);
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    final Transaction transaction = storageClient1.startStoreTransaction();
    transaction.putBlock(blockRoot, block);
    assertThat(transaction.commit()).isCompleted();
    return block;
  }

  private List<SignedBeaconBlock> requestBlocks(final List<Bytes32> blockRoots)
      throws InterruptedException, java.util.concurrent.ExecutionException,
          java.util.concurrent.TimeoutException {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(peer1.requestBlocksByRoot(blockRoots, blocks::add));
    return blocks;
  }

  public static class BeaconBlocksByRootIntegrationTest_ssz
      extends BeaconBlocksByRootIntegrationTest {

    @Override
    protected RpcEncoding getEncoding() {
      return RpcEncoding.SSZ;
    }
  }

  public static class BeaconBlocksByRootIntegrationTest_sszSnappy
      extends BeaconBlocksByRootIntegrationTest {
    @Override
    protected RpcEncoding getEncoding() {
      return RpcEncoding.SSZ_SNAPPY;
    }
  }
}
