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
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRootValidatorTest.breakInclusionProof;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@SuppressWarnings("JavaCase")
public class BlobSidecarsByRootListenerValidatingProxyTest {
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private BlobSidecarsByRootListenerValidatingProxy listenerWrapper;
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final KZG kzg = mock(KZG.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<BlobSidecar> listener = mock(RpcResponseListener.class);

  @BeforeEach
  void setUp() {
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(true);
  }

  @Test
  void blobSidecarsAreCorrect() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(2));
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(3));
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(4));
    final List<BlobIdentifier> blobIdentifiers =
        List.of(
            new BlobIdentifier(block1.getRoot(), UInt64.ZERO),
            new BlobIdentifier(block1.getRoot(), UInt64.ONE),
            new BlobIdentifier(block2.getRoot(), UInt64.ZERO),
            new BlobIdentifier(block2.getRoot(), UInt64.ONE), // will be missed, shouldn't be fatal
            new BlobIdentifier(block3.getRoot(), UInt64.ZERO),
            new BlobIdentifier(block4.getRoot(), UInt64.ZERO));
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(peer, spec, listener, kzg, blobIdentifiers);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 1);
    final BlobSidecar blobSidecar2_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block2, 0);
    final BlobSidecar blobSidecar3_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block3, 0);
    final BlobSidecar blobSidecar4_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block4, 0);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_1).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar2_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar3_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar4_0).join());
  }

  @Test
  void blobSidecarIdentifierNotRequested() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(2));
    final List<BlobIdentifier> blobIdentifiers =
        List.of(
            new BlobIdentifier(block1.getRoot(), UInt64.ZERO),
            new BlobIdentifier(block1.getRoot(), UInt64.ONE));
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(peer, spec, listener, kzg, blobIdentifiers);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 1);
    final BlobSidecar blobSidecar2_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block2, 0);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_1).join());
    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar2_0);
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
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final BlobIdentifier blobIdentifier = new BlobIdentifier(block1.getRoot(), UInt64.ZERO);
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, List.of(blobIdentifier));

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_KZG_VERIFICATION_FAILED
                .describe());
  }

  @Test
  void blobSidecarFailsInclusionProofVerification() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final BlobIdentifier blobIdentifier = new BlobIdentifier(block1.getRoot(), UInt64.ZERO);
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, List.of(blobIdentifier));

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_0_modified = breakInclusionProof(spec, blobSidecar1_0);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_0_modified);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED
                .describe());
  }
}
