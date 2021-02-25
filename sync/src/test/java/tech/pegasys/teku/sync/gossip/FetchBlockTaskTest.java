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

package tech.pegasys.teku.sync.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.sync.gossip.FetchBlockTask.FetchBlockResult;
import tech.pegasys.teku.sync.gossip.FetchBlockTask.FetchBlockResult.Status;

public class FetchBlockTaskTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  final Eth2P2PNetwork eth2P2PNetwork = mock(Eth2P2PNetwork.class);
  final List<Eth2Peer> peers = new ArrayList<>();

  @BeforeEach
  public void setup() {
    when(eth2P2PNetwork.streamPeers()).thenAnswer((invocation) -> peers.stream());
  }

  @Test
  public void run_successful() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    FetchBlockTask task = FetchBlockTask.create(eth2P2PNetwork, blockRoot);
    assertThat(task.getBlockRoot()).isEqualTo(blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    final SafeFuture<FetchBlockResult> result = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult = result.getNow(null);
    assertThat(fetchBlockResult.isSuccessful()).isTrue();
    assertThat(fetchBlockResult.getBlock()).isEqualTo(block);
  }

  @Test
  public void run_noPeers() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    FetchBlockTask task = FetchBlockTask.create(eth2P2PNetwork, blockRoot);

    final SafeFuture<FetchBlockResult> result = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult = result.getNow(null);
    assertThat(fetchBlockResult.isSuccessful()).isFalse();
    assertThat(fetchBlockResult.getStatus()).isEqualTo(Status.NO_AVAILABLE_PEERS);
  }

  @Test
  public void run_failAndRetryWithNoNewPeers() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    FetchBlockTask task = FetchBlockTask.create(eth2P2PNetwork, blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final SafeFuture<FetchBlockResult> result = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult = result.getNow(null);
    assertThat(fetchBlockResult.isSuccessful()).isFalse();
    assertThat(fetchBlockResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Retry
    final SafeFuture<FetchBlockResult> result2 = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult2 = result2.getNow(null);
    assertThat(fetchBlockResult2.isSuccessful()).isFalse();
    assertThat(fetchBlockResult2.getStatus()).isEqualTo(Status.NO_AVAILABLE_PEERS);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);
  }

  @Test
  public void run_failAndRetryWithNewPeer() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    FetchBlockTask task = FetchBlockTask.create(eth2P2PNetwork, blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final SafeFuture<FetchBlockResult> result = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult = result.getNow(null);
    assertThat(fetchBlockResult.isSuccessful()).isFalse();
    assertThat(fetchBlockResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Add another peer
    final Eth2Peer peer2 = registerNewPeer(2);
    when(peer2.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    // Retry
    final SafeFuture<FetchBlockResult> result2 = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult2 = result2.getNow(null);
    assertThat(fetchBlockResult2.isSuccessful()).isTrue();
    assertThat(fetchBlockResult2.getBlock()).isEqualTo(block);
    assertThat(task.getNumberOfRetries()).isEqualTo(1);
  }

  @Test
  public void run_withMultiplesPeersAvailable() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    FetchBlockTask task = FetchBlockTask.create(eth2P2PNetwork, blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));
    when(peer.getOutstandingRequests()).thenReturn(1);
    // Add another peer
    final Eth2Peer peer2 = registerNewPeer(2);
    when(peer2.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(peer2.getOutstandingRequests()).thenReturn(0);

    // We should choose the peer that is less busy, which successfully returns the block
    final SafeFuture<FetchBlockResult> result = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult = result.getNow(null);
    assertThat(fetchBlockResult.isSuccessful()).isTrue();
    assertThat(fetchBlockResult.getBlock()).isEqualTo(block);
  }

  @Test
  public void cancel() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    FetchBlockTask task = FetchBlockTask.create(eth2P2PNetwork, blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    task.cancel();
    final SafeFuture<FetchBlockResult> result = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult = result.getNow(null);
    assertThat(fetchBlockResult.isSuccessful()).isFalse();
    assertThat(fetchBlockResult.getStatus()).isEqualTo(Status.CANCELLED);
  }

  private Eth2Peer registerNewPeer(final int id) {
    final Eth2Peer peer = mock(Eth2Peer.class);
    when(peer.getOutstandingRequests()).thenReturn(0);
    when(peer.getId()).thenReturn(new MockNodeId(id));

    peers.add(peer);
    return peer;
  }
}
