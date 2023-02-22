package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

// TODO: add more test cases once ChainBuilder is updated for BlobSidecars
public class BlobSidecarsByRootIntegrationTest extends AbstractRpcMethodIntegrationTest {

  @Test
  public void shouldFailBeforeDenebMilestone() {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalCapella());
    assertThatThrownBy(() -> requestBlobSidecars(peer, List.of()))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("BlobSidecarsByRoot method is not available");
  }

  private List<BlobSidecar> requestBlobSidecars(
      final Eth2Peer peer, final List<BlobIdentifier> blobIdentifiers)
      throws InterruptedException, ExecutionException, TimeoutException, RpcException {
    final List<BlobSidecar> blobSidecars = new ArrayList<>();
    waitFor(
        peer.requestBlobSidecarsByRoot(
            blobIdentifiers, RpcResponseListener.from(blobSidecars::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return blobSidecars;
  }
}
