/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.beacon.sync.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class FetchBlobSidecarsTaskTest extends AbstractFetchTaskTest {

  @Test
  public void run_successful() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(3);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobIdentifier> blobIdentifiers = getBlobIdentifiers(blobSidecars);

    final FetchBlobSidecarsTask task =
        new FetchBlobSidecarsTask(eth2P2PNetwork, block.getRoot(), blobIdentifiers);
    assertThat(task.getKey()).isEqualTo(block.getRoot());

    final Eth2Peer peer = registerNewPeer(1);
    mockRpcResponse(peer, blobIdentifiers, blobSidecars);

    final SafeFuture<FetchResult<List<BlobSidecar>>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(blobSidecars);
  }

  @Test
  public void run_noPeers() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(3);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobIdentifier> blobIdentifiers = getBlobIdentifiers(blobSidecars);

    final FetchBlobSidecarsTask task =
        new FetchBlobSidecarsTask(eth2P2PNetwork, block.getRoot(), blobIdentifiers);

    final SafeFuture<FetchResult<List<BlobSidecar>>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).isEmpty();
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.NO_AVAILABLE_PEERS);
  }

  @Test
  public void run_failAndRetryWithNoNewPeers() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(3);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobIdentifier> blobIdentifiers = getBlobIdentifiers(blobSidecars);

    final FetchBlobSidecarsTask task =
        new FetchBlobSidecarsTask(eth2P2PNetwork, block.getRoot(), blobIdentifiers);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlobSidecarsByRoot(eq(blobIdentifiers), any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final SafeFuture<FetchResult<List<BlobSidecar>>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Retry
    final SafeFuture<FetchResult<List<BlobSidecar>>> result2 = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult2 = result2.getNow(null);
    assertThat(fetchResult2.getPeer()).isEmpty();
    assertThat(fetchResult2.isSuccessful()).isFalse();
    assertThat(fetchResult2.getStatus()).isEqualTo(Status.NO_AVAILABLE_PEERS);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);
  }

  @Test
  public void run_failAndRetryWithNewPeer() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(3);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobIdentifier> blobIdentifiers = getBlobIdentifiers(blobSidecars);

    final FetchBlobSidecarsTask task =
        new FetchBlobSidecarsTask(eth2P2PNetwork, block.getRoot(), blobIdentifiers);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlobSidecarsByRoot(eq(blobIdentifiers), any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final SafeFuture<FetchResult<List<BlobSidecar>>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Add another peer
    final Eth2Peer peer2 = registerNewPeer(2);
    mockRpcResponse(peer2, blobIdentifiers, blobSidecars);

    // Retry
    final SafeFuture<FetchResult<List<BlobSidecar>>> result2 = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult2 = result2.getNow(null);
    assertThat(fetchResult2.getPeer()).hasValue(peer2);
    assertThat(fetchResult2.isSuccessful()).isTrue();
    assertThat(fetchResult2.getResult()).hasValue(blobSidecars);
    assertThat(task.getNumberOfRetries()).isEqualTo(1);
  }

  @Test
  public void run_withMultiplesPeersAvailable() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(3);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobIdentifier> blobIdentifiers = getBlobIdentifiers(blobSidecars);

    final FetchBlobSidecarsTask task =
        new FetchBlobSidecarsTask(eth2P2PNetwork, block.getRoot(), blobIdentifiers);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlobSidecarsByRoot(eq(blobIdentifiers), any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));
    when(peer.getOutstandingRequests()).thenReturn(1);
    // Add another peer
    final Eth2Peer peer2 = registerNewPeer(2);
    mockRpcResponse(peer2, blobIdentifiers, blobSidecars);
    when(peer2.getOutstandingRequests()).thenReturn(0);

    // We should choose the peer that is less busy, which successfully returns the blob sidecar
    final SafeFuture<FetchResult<List<BlobSidecar>>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer2);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(blobSidecars);
  }

  @Test
  public void run_withPreferredPeer() {
    final Eth2Peer preferredPeer = createNewPeer(1);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(3);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobIdentifier> blobIdentifiers = getBlobIdentifiers(blobSidecars);
    mockRpcResponse(preferredPeer, blobIdentifiers, blobSidecars);

    final FetchBlobSidecarsTask task =
        new FetchBlobSidecarsTask(
            eth2P2PNetwork, Optional.of(preferredPeer), block.getRoot(), blobIdentifiers);

    // Add a peer
    registerNewPeer(2);

    final SafeFuture<FetchResult<List<BlobSidecar>>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(preferredPeer);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(blobSidecars);
  }

  @Test
  public void run_withRandomPeerWhenFetchingWithPreferredPeerFails() {
    final Eth2Peer preferredPeer = createNewPeer(1);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(3);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobIdentifier> blobIdentifiers = getBlobIdentifiers(blobSidecars);

    when(preferredPeer.requestBlobSidecarsByRoot(eq(blobIdentifiers), any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final FetchBlobSidecarsTask task =
        new FetchBlobSidecarsTask(
            eth2P2PNetwork, Optional.of(preferredPeer), block.getRoot(), blobIdentifiers);

    final SafeFuture<FetchResult<List<BlobSidecar>>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(preferredPeer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Add a peer
    final Eth2Peer peer = registerNewPeer(2);
    mockRpcResponse(peer, blobIdentifiers, blobSidecars);

    // Retry
    final SafeFuture<FetchResult<List<BlobSidecar>>> result2 = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult2 = result2.getNow(null);
    assertThat(fetchResult2.getPeer()).hasValue(peer);
    assertThat(fetchResult2.isSuccessful()).isTrue();
    assertThat(fetchResult2.getResult()).hasValue(blobSidecars);
    assertThat(task.getNumberOfRetries()).isEqualTo(1);
  }

  @Test
  public void cancel() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(3);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobIdentifier> blobIdentifiers = getBlobIdentifiers(blobSidecars);

    final FetchBlobSidecarsTask task =
        new FetchBlobSidecarsTask(eth2P2PNetwork, block.getRoot(), blobIdentifiers);
    assertThat(task.getKey()).isEqualTo(block.getRoot());

    final Eth2Peer peer = registerNewPeer(1);
    mockRpcResponse(peer, blobIdentifiers, blobSidecars);

    task.cancel();
    final SafeFuture<FetchResult<List<BlobSidecar>>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<List<BlobSidecar>> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).isEmpty();
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.CANCELLED);
  }

  private List<BlobIdentifier> getBlobIdentifiers(final List<BlobSidecar> blobSidecars) {
    return blobSidecars.stream()
        .map(sidecar -> new BlobIdentifier(sidecar.getBlockRoot(), sidecar.getIndex()))
        .toList();
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void mockRpcResponse(
      final Eth2Peer peer,
      final List<BlobIdentifier> blobIdentifiers,
      final List<BlobSidecar> blobSidecars) {
    when(peer.requestBlobSidecarsByRoot(eq(blobIdentifiers), any()))
        .thenAnswer(
            invocationOnMock -> {
              final RpcResponseHandler<BlobSidecar> handler = invocationOnMock.getArgument(1);
              blobSidecars.forEach(handler::onResponse);
              handler.onCompleted();
              return SafeFuture.COMPLETE;
            });
  }
}
