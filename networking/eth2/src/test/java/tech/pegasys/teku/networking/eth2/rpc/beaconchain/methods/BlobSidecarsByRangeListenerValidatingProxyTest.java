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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRootValidatorTest.breakInclusionProof;

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
import tech.pegasys.teku.spec.util.DataStructureUtil;

@SuppressWarnings("JavaCase")
public class BlobSidecarsByRangeListenerValidatingProxyTest {
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private BlobSidecarsByRangeListenerValidatingProxy listenerWrapper;
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final Integer maxBlobsPerBlock = spec.getMaxBlobsPerBlock().orElseThrow();
  private final KZG kzg = mock(KZG.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<BlobSidecar> listener = mock(RpcResponseListener.class);

  @BeforeEach
  void setUp() {
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(true);
  }

  @Test
  void blobSidecarFailsKzgVerification() {
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(false);
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(ONE), 0);

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
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(ONE), 0);
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

  @Test
  void blobSidecarSlotSmallerThanFromSlot() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final BlobSidecar blobSidecar0_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(ZERO), 0);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar0_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_SLOT_NOT_IN_RANGE
                .describe());
  }

  @Test
  void blobSidecarsSlotsAreCorrect() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 1);
    final BlobSidecar blobSidecar2_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(2, block1.getRoot()), 0);
    final BlobSidecar blobSidecar3_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(3, blobSidecar2_0.getBlockRoot()), 0);
    final BlobSidecar blobSidecar4_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(4, blobSidecar3_0.getBlockRoot()), 0);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_1).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar2_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar3_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar4_0).join());
  }

  @Test
  void blobSidecarSlotGreaterThanToSlot() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(8);
    // This requests 8 slots (1, 2, 3, 4, 5, 6, 7, 8) so 9 will be unexpected.
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(1), 0);
    final BlobSidecar blobSidecar3_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(3), 0);
    final BlobSidecar blobSidecar5_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(5), 0);
    final BlobSidecar blobSidecar8_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(8), 0);
    final BlobSidecar blobSidecar9_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(9, blobSidecar8_0.getBlockRoot()), 0);

    safeJoin(listenerWrapper.onResponse(blobSidecar1_0));
    safeJoin(listenerWrapper.onResponse(blobSidecar3_0));
    safeJoin(listenerWrapper.onResponse(blobSidecar5_0));
    safeJoin(listenerWrapper.onResponse(blobSidecar8_0));

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar9_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_SLOT_NOT_IN_RANGE
                .describe());
  }

  @Test
  void blobSidecarParentRootDoesNotMatch() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(1), 0);
    final BlobSidecar blobSidecar2_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(2), 0);

    safeJoin(listenerWrapper.onResponse(blobSidecar1_0));

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar2_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNKNOWN_PARENT
                .describe());
  }

  @Test
  void blobSidecarIndexIsGreaterOrEqualThanMaxBlobs() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);

    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(ONE, 7);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 1);
    final BlobSidecar blobSidecar1_2 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 2);
    final BlobSidecar blobSidecar1_3 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 3);
    final BlobSidecar blobSidecar1_4 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 4);
    final BlobSidecar blobSidecar1_5 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 5);
    final BlobSidecar blobSidecar1_6 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 6);

    safeJoin(listenerWrapper.onResponse(blobSidecar1_0));
    safeJoin(listenerWrapper.onResponse(blobSidecar1_1));
    safeJoin(listenerWrapper.onResponse(blobSidecar1_2));
    safeJoin(listenerWrapper.onResponse(blobSidecar1_3));
    safeJoin(listenerWrapper.onResponse(blobSidecar1_4));
    safeJoin(listenerWrapper.onResponse(blobSidecar1_5));

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_6);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_INDEX
                .describe());
  }

  @Test
  void blobSidecarIndexIsInTheSameBlockButNotNext() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);

    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(ONE, 3);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_2 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 2);

    safeJoin(listenerWrapper.onResponse(blobSidecar1_0));

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_2);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_INDEX
                .describe());
  }

  @Test
  void isFirstBlobSidecarAfterAnEmptyBlobsBlock() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);

    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 1);
    final BlobSidecar blobSidecar3_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(3), 0);

    safeJoin(listenerWrapper.onResponse(blobSidecar1_0));
    safeJoin(listenerWrapper.onResponse(blobSidecar1_1));
    safeJoin(listenerWrapper.onResponse(blobSidecar3_0));
  }

  @Test
  void firstBlobSidecarIndexIsINotZero() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(2), 1);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_1);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_INDEX
                .describe());
  }

  @Test
  void firstBlobSidecarIndexInNextBlockIsNotZero() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(1), 0);
    final BlobSidecar blobSidecar2_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(2, blobSidecar1_0.getBlockRoot(), true), 1);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_0).join());

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar2_1);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_INDEX
                .describe());
  }

  @Test
  void blobSidecarUnexpectedSlot() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final BlobSidecar blobSidecar2_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(2), 0);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(1), 0);

    safeJoin(listenerWrapper.onResponse(blobSidecar2_0));
    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_SLOT
                .describe());
  }
}
