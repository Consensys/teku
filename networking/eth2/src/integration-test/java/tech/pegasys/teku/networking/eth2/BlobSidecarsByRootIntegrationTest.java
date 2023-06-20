/*
 * Copyright ConsenSys Software Inc., 2023
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class BlobSidecarsByRootIntegrationTest extends AbstractRpcMethodIntegrationTest {

  @Test
  public void requestBlobSidecars_shouldFailBeforeDenebMilestone() {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalCapella());
    assertThatThrownBy(() -> requestBlobSidecarsByRoot(peer, List.of()))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("BlobSidecarsByRoot method is not supported");
  }

  @Test
  public void requestBlobSidecar_shouldFailBeforeDenebMilestone() {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalCapella());
    assertThatThrownBy(
            () -> requestBlobSidecarByRoot(peer, new BlobIdentifier(Bytes32.ZERO, UInt64.ZERO)))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("BlobSidecarsByRoot method is not supported");
  }

  @Test
  public void requestBlobSidecars_shouldReturnEmptyBlobSidecarsOnDenebMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalDeneb());
    final Optional<BlobSidecar> blobSidecar =
        requestBlobSidecarByRoot(peer, new BlobIdentifier(Bytes32.ZERO, UInt64.ZERO));
    assertThat(blobSidecar).isEmpty();
  }

  @Test
  public void requestBlobSidecars_shouldReturnBlobSidecarsOnDenebMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalDeneb());

    // generate 4 blobs per block
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobs(true);
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    peerStorage.chainUpdater().advanceChainUntil(targetSlot);

    // grab expected blobs from storage
    final List<BlobSidecar> expectedBlobSidecars =
        retrieveCanonicalBlobSidecarsFromPeerStorage(Stream.of(UInt64.ONE, UInt64.valueOf(3)));

    // request all expected plus a non existing
    List<BlobIdentifier> requestedBlobIds =
        Stream.concat(
                Stream.of(new BlobIdentifier(Bytes32.ZERO, UInt64.ZERO)),
                expectedBlobSidecars.stream()
                    .map(sidecar -> new BlobIdentifier(sidecar.getBlockRoot(), sidecar.getIndex())))
            .collect(Collectors.toUnmodifiableList());

    final List<BlobSidecar> blobSidecars = requestBlobSidecarsByRoot(peer, requestedBlobIds);

    assertThat(blobSidecars).containsExactlyInAnyOrderElementsOf(expectedBlobSidecars);
  }

  private List<BlobSidecar> requestBlobSidecarsByRoot(
      final Eth2Peer peer, final List<BlobIdentifier> blobIdentifiers)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<BlobSidecar> blobSidecars = new ArrayList<>();
    waitFor(
        peer.requestBlobSidecarsByRoot(
            blobIdentifiers, RpcResponseListener.from(blobSidecars::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return blobSidecars;
  }

  private Optional<BlobSidecar> requestBlobSidecarByRoot(
      final Eth2Peer peer, final BlobIdentifier blobIdentifier)
      throws ExecutionException, InterruptedException, TimeoutException {
    final Optional<BlobSidecar> blobSidecar =
        waitFor(peer.requestBlobSidecarByRoot(blobIdentifier));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return blobSidecar;
  }
}
