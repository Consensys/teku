/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarOld;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlobSidecarsByRootListenerValidatingProxyTest {
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private BlobSidecarsByRootListenerValidatingProxy listenerWrapper;
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final KZG kzg = mock(KZG.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<BlobSidecarOld> listener = mock(RpcResponseListener.class);

  @BeforeEach
  void setUp() {
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(true);
  }

  @Test
  void blobSidecarsAreCorrect() {
    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot3 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot4 = dataStructureUtil.randomBytes32();
    final List<BlobIdentifier> blobIdentifiers =
        List.of(
            new BlobIdentifier(blockRoot1, UInt64.ZERO),
            new BlobIdentifier(blockRoot1, UInt64.ONE),
            new BlobIdentifier(blockRoot2, UInt64.ZERO),
            new BlobIdentifier(blockRoot2, UInt64.ONE), // will be missed, shouldn't be fatal
            new BlobIdentifier(blockRoot3, UInt64.ZERO),
            new BlobIdentifier(blockRoot4, UInt64.ZERO));
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(peer, spec, listener, kzg, blobIdentifiers);

    final BlobSidecarOld blobSidecar10 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);
    final BlobSidecarOld blobSidecar11 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ONE);
    final BlobSidecarOld blobSidecar2 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(2), blockRoot2, blockRoot1, UInt64.ZERO);
    final BlobSidecarOld blobSidecar3 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(3), blockRoot3, blockRoot2, UInt64.ZERO);
    final BlobSidecarOld blobSidecar4 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(4), blockRoot4, blockRoot3, UInt64.ZERO);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar10).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar11).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar2).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar3).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar4).join());
  }

  @Test
  void blobSidecarIdentifierNotRequested() {
    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();
    final List<BlobIdentifier> blobIdentifiers =
        List.of(
            new BlobIdentifier(blockRoot1, UInt64.ZERO),
            new BlobIdentifier(blockRoot1, UInt64.ONE));
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(peer, spec, listener, kzg, blobIdentifiers);

    final BlobSidecarOld blobSidecar10 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);
    final BlobSidecarOld blobSidecar11 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ONE);
    final BlobSidecarOld blobSidecar2 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(2), blockRoot2, blockRoot1, UInt64.ZERO);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar10).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar11).join());
    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar2);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_IDENTIFIER
                .describe());
  }

  @Test
  void blobSidecarFailsKzgVerification() {
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(false);
    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobIdentifier blobIdentifier = new BlobIdentifier(blockRoot1, UInt64.ZERO);
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, List.of(blobIdentifier));

    final BlobSidecarOld blobSidecar1 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_KZG_VERIFICATION_FAILED
                .describe());
  }
}
