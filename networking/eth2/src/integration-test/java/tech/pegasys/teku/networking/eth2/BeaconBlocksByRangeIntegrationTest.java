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

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class BeaconBlocksByRangeIntegrationTest {

  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Peer peer1;
  private RecentChainData recentChainData1;
  private BeaconChainUtil beaconChainUtil;

  @BeforeEach
  public void setUp() throws Exception {
    final EventBus eventBus1 = new EventBus();
    final RpcEncoding rpcEncoding = getEncoding();
    recentChainData1 = MemoryOnlyRecentChainData.create(eventBus1);
    beaconChainUtil = BeaconChainUtil.create(1, recentChainData1);
    beaconChainUtil.initializeStorage();

    final Eth2Network network1 =
        networkFactory
            .builder()
            .rpcEncoding(rpcEncoding)
            .eventBus(eventBus1)
            .recentChainData(recentChainData1)
            .startNetwork();

    final RecentChainData recentChainData2 = MemoryOnlyRecentChainData.create(new EventBus());
    BeaconChainUtil.create(1, recentChainData2).initializeStorage();
    final Eth2Network network2 =
        networkFactory
            .builder()
            .rpcEncoding(rpcEncoding)
            .peer(network1)
            .recentChainData(recentChainData2)
            .startNetwork();
    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
  }

  protected abstract RpcEncoding getEncoding();

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
    final SignedBeaconBlock block1 = beaconChainUtil.createAndImportBlockAtSlot(1);
    final Bytes32 block1Root = block1.getMessage().hash_tree_root();
    recentChainData1.updateHead(block1Root, block1.getSlot());

    final SignedBeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2);
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    recentChainData1.updateHead(block2Root, block2.getSlot());

    final List<SignedBeaconBlock> response = requestBlocks();
    assertThat(response).containsExactly(block1, block2);
  }

  @Test
  public void requestBlocksByRangeAfterPeerDisconnectedImmediately() throws Exception {
    // Setup chain
    beaconChainUtil.createAndImportBlockAtSlot(1);
    final SignedBeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2);
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    recentChainData1.updateHead(block2Root, block2.getSlot());

    peer1.disconnectImmediately(Optional.empty(), false);
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer1.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), UInt64.ONE, ResponseStreamListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlocksByRangeAfterPeerDisconnected() throws Exception {
    // Setup chain
    beaconChainUtil.createAndImportBlockAtSlot(1);
    final SignedBeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2);
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    recentChainData1.updateHead(block2Root, block2.getSlot());

    Waiter.waitFor(peer1.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer1.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), UInt64.ONE, ResponseStreamListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlockBySlotAfterPeerDisconnectedImmediately() throws Exception {
    // Setup chain
    beaconChainUtil.createAndImportBlockAtSlot(1);
    final SignedBeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2);
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    recentChainData1.updateHead(block2Root, block2.getSlot());

    peer1.disconnectImmediately(Optional.empty(), false);
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer1.requestBlockBySlot(UInt64.ONE);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnected() throws Exception {
    // Setup chain
    beaconChainUtil.createAndImportBlockAtSlot(1);
    final SignedBeaconBlock block2 = beaconChainUtil.createAndImportBlockAtSlot(2);
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    recentChainData1.updateHead(block2Root, block2.getSlot());

    Waiter.waitFor(peer1.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer1.requestBlockBySlot(UInt64.ONE);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  void requestBlockBySlot_shouldReturnEmptyWhenBlockIsNotKnown() throws Exception {
    final Optional<SignedBeaconBlock> result =
        waitFor(peer1.requestBlockBySlot(UInt64.valueOf(49382982)));
    assertThat(result).isEmpty();
  }

  private List<SignedBeaconBlock> requestBlocks()
      throws InterruptedException, java.util.concurrent.ExecutionException,
          java.util.concurrent.TimeoutException {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer1.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), UInt64.ONE, ResponseStreamListener.from(blocks::add)));
    return blocks;
  }

  public static class BeaconBlocksByRangeIntegrationTest_ssz
      extends BeaconBlocksByRangeIntegrationTest {

    @Override
    protected RpcEncoding getEncoding() {
      return RpcEncoding.SSZ;
    }
  }

  public static class BeaconBlocksByRangeIntegrationTest_sszSnappy
      extends BeaconBlocksByRangeIntegrationTest {

    @Override
    protected RpcEncoding getEncoding() {
      return RpcEncoding.SSZ_SNAPPY;
    }
  }
}
