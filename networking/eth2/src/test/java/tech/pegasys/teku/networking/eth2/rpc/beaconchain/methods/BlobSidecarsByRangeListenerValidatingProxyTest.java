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
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.util.DataStructureUtil;

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

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar1 =
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

  @Test
  void blobSidecarSlotSmallerThanFromSlot() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final BlobSidecar blobSidecar0 =
        dataStructureUtil.randomBlobSidecar(
            UInt64.ZERO, dataStructureUtil.randomBytes32(), UInt64.ZERO);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar0);
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

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar10 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);
    final BlobSidecar blobSidecar11 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ONE);
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar2 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(2), blockRoot2, blockRoot1, UInt64.ZERO);
    final Bytes32 blockRoot3 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar3 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(3), blockRoot3, blockRoot2, UInt64.ZERO);
    final Bytes32 blockRoot4 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar4 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(4), blockRoot4, blockRoot3, UInt64.ZERO);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar10).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar11).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar2).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar3).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar4).join());
  }

  @Test
  void blobSidecarSlotGreaterThanToSlot() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(8);
    // This requests 8 slots (1, 2, 3, 4, 5, 6, 7, 8) so 9 will be unexpected.
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar1 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar2 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(3), blockRoot2, blockRoot1, UInt64.ZERO);
    final Bytes32 blockRoot3 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar3 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(5), blockRoot3, blockRoot2, UInt64.ZERO);
    final Bytes32 blockRoot4 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar4 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(8), blockRoot4, blockRoot3, UInt64.ZERO);
    final Bytes32 blockRoot5 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar5 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(9), blockRoot5, blockRoot4, UInt64.ZERO);

    listenerWrapper.onResponse(blobSidecar1).join();
    listenerWrapper.onResponse(blobSidecar2).join();
    listenerWrapper.onResponse(blobSidecar3).join();
    listenerWrapper.onResponse(blobSidecar4).join();

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar5);
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

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar1 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar2 =
        dataStructureUtil.randomBlobSidecar(
            UInt64.valueOf(2), blockRoot2, dataStructureUtil.randomBytes32(), UInt64.ZERO);

    listenerWrapper.onResponse(blobSidecar1).join();

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar2);
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

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar1 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);
    final BlobSidecar blobSidecar2 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ONE);
    final BlobSidecar blobSidecar3 =
        dataStructureUtil.randomBlobSidecar(
            UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.valueOf(2));
    final BlobSidecar blobSidecar4 =
        dataStructureUtil.randomBlobSidecar(
            UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.valueOf(3));
    final BlobSidecar blobSidecar5 =
        dataStructureUtil.randomBlobSidecar(
            UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.valueOf(4));
    final BlobSidecar blobSidecar6 =
        dataStructureUtil.randomBlobSidecar(
            UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.valueOf(5));
    final BlobSidecar blobSidecar7 =
        dataStructureUtil.randomBlobSidecar(
            UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.valueOf(6));

    listenerWrapper.onResponse(blobSidecar1).join();
    listenerWrapper.onResponse(blobSidecar2).join();
    listenerWrapper.onResponse(blobSidecar3).join();
    listenerWrapper.onResponse(blobSidecar4).join();
    listenerWrapper.onResponse(blobSidecar5).join();
    listenerWrapper.onResponse(blobSidecar6).join();

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar7);
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

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar1 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);
    final BlobSidecar blobSidecar2 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ONE);
    final BlobSidecar blobSidecar3 =
        dataStructureUtil.randomBlobSidecar(
            UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.valueOf(3));

    listenerWrapper.onResponse(blobSidecar1).join();
    listenerWrapper.onResponse(blobSidecar2).join();

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar3);
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
  void firstBlobSidecarIndexIsINotZero() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar1 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, UInt64.ONE);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1);
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

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar1 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar2 =
        dataStructureUtil.randomBlobSidecar(UInt64.valueOf(2), blockRoot2, blockRoot1, UInt64.ONE);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1).join());

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar2);
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
  void missedBlobSidecarIndex() {
    final UInt64 startSlot = UInt64.valueOf(1);
    final UInt64 count = UInt64.valueOf(4);

    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, kzg, startSlot, count);

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar1 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);
    final BlobSidecar blobSidecar2 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ONE);
    final BlobSidecar blobSidecar4 =
        dataStructureUtil.randomBlobSidecar(
            UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.valueOf(3));

    listenerWrapper.onResponse(blobSidecar1).join();
    listenerWrapper.onResponse(blobSidecar2).join();

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar4);
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

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar1 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot1, Bytes32.ZERO, UInt64.ZERO);
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();
    final BlobSidecar blobSidecar2 =
        dataStructureUtil.randomBlobSidecar(UInt64.ONE, blockRoot2, blockRoot1, UInt64.ZERO);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1).join());

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar2);
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
