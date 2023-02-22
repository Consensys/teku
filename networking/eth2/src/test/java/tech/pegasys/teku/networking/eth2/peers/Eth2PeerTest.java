/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.peers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer.PeerStatusSubscriber;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobsSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class Eth2PeerTest {

  private final UInt64 denebForkEpoch = UInt64.ONE;
  private final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(denebForkEpoch);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Peer delegate = mock(Peer.class);
  private final BeaconChainMethods rpcMethods = mock(BeaconChainMethods.class);
  private final StatusMessageFactory statusMessageFactory = mock(StatusMessageFactory.class);
  private final MetadataMessagesFactory metadataMessagesFactory =
      mock(MetadataMessagesFactory.class);
  private final PeerChainValidator peerChainValidator = mock(PeerChainValidator.class);
  private final RateTracker blockRateTracker = mock(RateTracker.class);
  private final RateTracker blobsSidecarsRateTracker = mock(RateTracker.class);
  private final RateTracker rateTracker = mock(RateTracker.class);

  private final PeerStatus randomPeerStatus = randomPeerStatus();

  private final Eth2Peer peer =
      Eth2Peer.create(
          spec,
          delegate,
          rpcMethods,
          statusMessageFactory,
          metadataMessagesFactory,
          peerChainValidator,
          blockRateTracker,
          blobsSidecarsRateTracker,
          rateTracker);

  @Test
  void updateStatus_shouldNotUpdateUntilValidationPasses() {
    final PeerStatusSubscriber peerStatusSubscriber = mock(PeerStatusSubscriber.class);
    peer.subscribeInitialStatus(peerStatusSubscriber);
    final SafeFuture<Boolean> validationResult = new SafeFuture<>();
    when(peerChainValidator.validate(peer, randomPeerStatus)).thenReturn(validationResult);

    peer.updateStatus(randomPeerStatus);

    verify(peerChainValidator).validate(peer, randomPeerStatus);
    assertThat(peer.hasStatus()).isFalse();
    verifyNoInteractions(peerStatusSubscriber);

    validationResult.complete(true);
    assertThat(peer.hasStatus()).isTrue();
    assertThat(peer.getStatus()).isEqualTo(randomPeerStatus);
    verify(peerStatusSubscriber).onPeerStatus(randomPeerStatus);
  }

  @Test
  void shouldCallCheckPeerIdentity() {
    final SafeFuture<Boolean> validationResult = new SafeFuture<>();
    when(peerChainValidator.validate(peer, randomPeerStatus)).thenReturn(validationResult);
    peer.updateStatus(randomPeerStatus);
    validationResult.complete(true);
    verify(delegate).checkPeerIdentity();
  }

  @Test
  void updateStatus_shouldNotUpdateStatusWhenValidationFails() {
    when(peerChainValidator.validate(peer, randomPeerStatus))
        .thenReturn(SafeFuture.completedFuture(false));

    peer.updateStatus(randomPeerStatus);

    assertThat(peer.hasStatus()).isFalse();
  }

  @Test
  void updateStatus_shouldNotUpdateSubsequentStatusWhenValidationFails() {
    final PeerStatus status2 = randomPeerStatus();
    when(peerChainValidator.validate(peer, randomPeerStatus))
        .thenReturn(SafeFuture.completedFuture(true));
    when(peerChainValidator.validate(peer, status2)).thenReturn(SafeFuture.completedFuture(false));

    peer.updateStatus(randomPeerStatus);

    assertThat(peer.hasStatus()).isTrue();

    peer.updateStatus(status2);

    // Status stays as the original peer status
    assertThat(peer.hasStatus()).isTrue();
    assertThat(peer.getStatus()).isEqualTo(randomPeerStatus);
  }

  @Test
  void updateStatus_shouldDisconnectPeerIfStatusValidationCompletesExceptionally() {
    when(peerChainValidator.validate(peer, randomPeerStatus))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Doh!")));

    peer.updateStatus(randomPeerStatus);

    assertThat(peer.hasStatus()).isFalse();
    verify(delegate).disconnectCleanly(DisconnectReason.UNABLE_TO_VERIFY_NETWORK);
  }

  @Test
  void updateStatus_shouldReportAllStatusesToSubscribers() {
    final PeerStatus status1 = randomPeerStatus();
    final PeerStatus status2 = randomPeerStatus();
    when(peerChainValidator.validate(any(), any())).thenReturn(SafeFuture.completedFuture(true));
    final PeerStatusSubscriber initialSubscriber = mock(PeerStatusSubscriber.class);
    final PeerStatusSubscriber subscriber = mock(PeerStatusSubscriber.class);

    peer.subscribeInitialStatus(initialSubscriber);
    peer.subscribeStatusUpdates(subscriber);

    peer.updateStatus(status1);

    verify(initialSubscriber).onPeerStatus(status1);
    verify(subscriber).onPeerStatus(status1);

    peer.updateStatus(status2);

    verify(initialSubscriber, never()).onPeerStatus(status2);
    verify(subscriber).onPeerStatus(status2);
  }

  @Test
  @SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
  public void shouldModifyRequestSpanningTheDenebForkTransition() {

    final Eth2RpcMethod<BlobsSidecarsByRangeRequestMessage, BlobsSidecar>
        blobsSidecarsByRangeMethod = mock(Eth2RpcMethod.class);

    final RpcStreamController<RpcRequestHandler> rpcStreamController =
        mock(RpcStreamController.class);

    when(rpcMethods.blobsSidecarsByRange()).thenReturn(Optional.of(blobsSidecarsByRangeMethod));

    when(peer.sendRequest(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(rpcStreamController));

    peer.requestBlobsSidecarsByRange(UInt64.ONE, UInt64.valueOf(13), __ -> SafeFuture.COMPLETE);

    final ArgumentCaptor<BlobsSidecarsByRangeRequestMessage> requestCaptor =
        ArgumentCaptor.forClass(BlobsSidecarsByRangeRequestMessage.class);

    verify(delegate, times(1)).sendRequest(any(), requestCaptor.capture(), any());

    final BlobsSidecarsByRangeRequestMessage request = requestCaptor.getValue();

    // Deneb starts from epoch 1, so request start slot should be 8 and the count should be 6
    assertThat(request.getStartSlot()).isEqualTo(UInt64.valueOf(8));
    assertThat(request.getCount()).isEqualTo(UInt64.valueOf(6));
  }

  private PeerStatus randomPeerStatus() {
    return new PeerStatus(
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64());
  }
}
