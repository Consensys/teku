/*
 * Copyright Consensys Software Inc., 2026
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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.peers.DataColumnSidecarSignatureValidator;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

@SuppressWarnings("JavaCase")
public abstract class AbstractDataColumnSidecarsByRangeListenerValidatingProxyTest {

  protected Spec spec;
  protected DataStructureUtil dataStructureUtil;
  protected DataColumnSidecarsByRangeListenerValidatingProxy listenerWrapper;

  protected final Eth2Peer peer = mock(Eth2Peer.class);
  protected final KZG kzg = mock(KZG.class);
  protected final MetricsSystem metricsSystem = new StubMetricsSystem();
  protected final TimeProvider timeProvider = StubTimeProvider.withTimeInMillis(ZERO);

  @SuppressWarnings("unchecked")
  protected final RpcResponseListener<DataColumnSidecar> listener = mock(RpcResponseListener.class);

  protected final DataColumnSidecarSignatureValidator signatureValidator =
      mock(DataColumnSidecarSignatureValidator.class);
  protected final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  protected abstract Spec createSpec();

  protected abstract DataColumnSidecar createSidecarWithInvalidStructure(
      DataColumnSidecar validSidecar);

  protected SignedBeaconBlock createBlock(final UInt64 slot) {
    return dataStructureUtil.randomSignedBeaconBlock(slot);
  }

  @BeforeEach
  void setUp() {
    spec = createSpec();
    dataStructureUtil = new DataStructureUtil(spec);
    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(true);
    when(signatureValidator.validateSignature(any())).thenReturn(SafeFuture.completedFuture(true));
  }

  @Test
  void dataColumnSidecarsAreCorrect() {
    final SignedBeaconBlock block1 = createBlock(ONE);
    final SignedBeaconBlock block2 = createBlock(UInt64.valueOf(2));
    final SignedBeaconBlock block3 = createBlock(UInt64.valueOf(3));
    final SignedBeaconBlock block4 = createBlock(UInt64.valueOf(4));

    final List<UInt64> columns = List.of(ZERO, ONE);

    listenerWrapper =
        new DataColumnSidecarsByRangeListenerValidatingProxy(
            spec,
            peer,
            listener,
            metricsSystem,
            timeProvider,
            signatureValidator,
            ONE,
            UInt64.valueOf(4),
            columns,
            combinedChainDataClient);

    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, ZERO);
    final DataColumnSidecar dataColumnSidecar1_1 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, ONE);
    final DataColumnSidecar dataColumnSidecar2_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block2, ZERO);
    final DataColumnSidecar dataColumnSidecar3_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block3, ZERO);
    final DataColumnSidecar dataColumnSidecar4_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block4, ZERO);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(dataColumnSidecar1_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(dataColumnSidecar1_1).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(dataColumnSidecar2_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(dataColumnSidecar3_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(dataColumnSidecar4_0).join());
  }

  @Test
  void blobSidecarNotInRange() {
    final SignedBeaconBlock block1 = createBlock(ONE);
    final SignedBeaconBlock block2 = createBlock(UInt64.valueOf(3));

    final List<UInt64> columns = List.of(ZERO, ONE);

    listenerWrapper =
        new DataColumnSidecarsByRangeListenerValidatingProxy(
            spec,
            peer,
            listener,
            metricsSystem,
            timeProvider,
            signatureValidator,
            ONE,
            UInt64.valueOf(2),
            columns,
            combinedChainDataClient);

    final DataColumnSidecar datColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, ZERO);
    final DataColumnSidecar datColumnSidecar1_1 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, ONE);
    final DataColumnSidecar datColumnSidecar2_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block2, ZERO);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(datColumnSidecar1_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(datColumnSidecar1_1).join());
    final SafeFuture<?> result = listenerWrapper.onResponse(datColumnSidecar2_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(DataColumnSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_SLOT_NOT_IN_RANGE
                .describe());
  }

  @Test
  void blobSidecarIdentifierNotRequested() {
    final SignedBeaconBlock block1 = createBlock(ONE);
    final SignedBeaconBlock block2 = createBlock(UInt64.valueOf(2));

    final List<UInt64> columns = List.of(ZERO, ONE);

    listenerWrapper =
        new DataColumnSidecarsByRangeListenerValidatingProxy(
            spec,
            peer,
            listener,
            metricsSystem,
            timeProvider,
            signatureValidator,
            ONE,
            UInt64.valueOf(2),
            columns,
            combinedChainDataClient);

    final DataColumnSidecar datColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, ZERO);
    final DataColumnSidecar datColumnSidecar1_1 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, ONE);
    final DataColumnSidecar datColumnSidecar2_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block2, UInt64.valueOf(3));

    assertDoesNotThrow(() -> listenerWrapper.onResponse(datColumnSidecar1_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(datColumnSidecar1_1).join());
    final SafeFuture<?> result = listenerWrapper.onResponse(datColumnSidecar2_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(DataColumnSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER
                .describe());
  }

  @Test
  void dataColumnSidecarFailsValidation() {
    final SignedBeaconBlock block1 = createBlock(ONE);

    final List<UInt64> columns = List.of(ZERO, ONE);

    listenerWrapper =
        new DataColumnSidecarsByRangeListenerValidatingProxy(
            spec,
            peer,
            listener,
            metricsSystem,
            timeProvider,
            signatureValidator,
            ONE,
            UInt64.valueOf(1),
            columns,
            combinedChainDataClient);

    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, ZERO);
    final DataColumnSidecar dataColumnSidecarModified =
        createSidecarWithInvalidStructure(dataColumnSidecar);

    final SafeFuture<?> result = listenerWrapper.onResponse(dataColumnSidecarModified);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(DataColumnSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_VALIDITY_CHECK_FAILED
                .describe());
  }

  @Test
  void dataColumnSidecarFailsKzgVerification() {
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(false);
    final SignedBeaconBlock block1 = createBlock(ONE);
    final List<UInt64> columns = List.of(ZERO, ONE);

    listenerWrapper =
        new DataColumnSidecarsByRangeListenerValidatingProxy(
            spec,
            peer,
            listener,
            metricsSystem,
            timeProvider,
            signatureValidator,
            ONE,
            UInt64.valueOf(1),
            columns,
            combinedChainDataClient);

    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, ZERO);

    final SafeFuture<?> result = listenerWrapper.onResponse(dataColumnSidecar);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(DataColumnSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED
                .describe());
  }

  @Test
  void dataColumnSidecarsFailsDueToSignatureVerification() {
    final SignedBeaconBlock block1 = createBlock(ONE);

    final List<UInt64> columns = List.of(ZERO, ONE);

    listenerWrapper =
        new DataColumnSidecarsByRangeListenerValidatingProxy(
            spec,
            peer,
            listener,
            metricsSystem,
            timeProvider,
            signatureValidator,
            ONE,
            UInt64.valueOf(1),
            columns,
            combinedChainDataClient);

    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, ZERO);

    when(signatureValidator.validateSignature(dataColumnSidecar1_0))
        .thenReturn(SafeFuture.completedFuture(false));

    final SafeFuture<?> result = listenerWrapper.onResponse(dataColumnSidecar1_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(DataColumnSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            InvalidResponseType.DATA_COLUMN_SIDECAR_HEADER_INVALID_SIGNATURE.describe());
  }
}
