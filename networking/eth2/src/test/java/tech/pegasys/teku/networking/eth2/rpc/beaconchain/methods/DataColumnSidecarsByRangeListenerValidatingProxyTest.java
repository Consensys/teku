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
import org.apache.tuweni.bytes.Bytes32;
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
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@SuppressWarnings("JavaCase")
public class DataColumnSidecarsByRangeListenerValidatingProxyTest {
  private final Spec spec = TestSpecFactory.createMainnetFulu();
  private final SchemaDefinitionsFulu schemaDefinitionsFulu =
      SchemaDefinitionsFulu.required(spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
  final DataColumnsByRootIdentifierSchema byRootIdentifierSchema =
      schemaDefinitionsFulu.getDataColumnsByRootIdentifierSchema();
  final DataColumnSidecarSchema<?> sidecarSchema =
      schemaDefinitionsFulu.getDataColumnSidecarSchema();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private DataColumnSidecarsByRangeListenerValidatingProxy listenerWrapper;
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final KZG kzg = mock(KZG.class);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final TimeProvider timeProvider = StubTimeProvider.withTimeInMillis(ZERO);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<DataColumnSidecar> listener = mock(RpcResponseListener.class);

  private final DataColumnSidecarSignatureValidator signatureValidator =
      mock(DataColumnSidecarSignatureValidator.class);

  @BeforeEach
  void setUp() {
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
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(2));
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(3));
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(4));

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
            columns);
    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);

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
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(3));

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
            columns);

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
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(2));

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
            columns);

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
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final DataColumnsByRootIdentifier dataColumnIdentifier =
        byRootIdentifierSchema.create(block1.getRoot(), ZERO);

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
            columns);

    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(
            block1, dataColumnIdentifier.getColumns().getFirst());
    final DataColumnSidecar dataColumnSidecarModified =
        sidecarSchema.create(
            builder ->
                builder
                    .index(dataColumnSidecar.getIndex())
                    .column(dataColumnSidecar.getColumn())
                    // replacing to empty commitments
                    .kzgCommitments(sidecarSchema.getKzgCommitmentsSchema().of())
                    .kzgProofs(dataColumnSidecar.getKzgProofs())
                    .signedBlockHeader(
                        DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader())
                    .kzgCommitmentsInclusionProof(
                        DataColumnSidecarFulu.required(dataColumnSidecar)
                            .getKzgCommitmentsInclusionProof()
                            .asListUnboxed()));

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
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final DataColumnsByRootIdentifier dataColumnIdentifier =
        byRootIdentifierSchema.create(block1.getRoot(), ZERO);
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
            columns);

    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(
            block1, dataColumnIdentifier.getColumns().getFirst());

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
  void dataColumnSidecarFailsInclusionProofVerification() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final DataColumnsByRootIdentifier dataColumnIdentifier =
        byRootIdentifierSchema.create(block1.getRoot(), ZERO);

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
            columns);

    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(
            block1, dataColumnIdentifier.getColumns().getFirst());
    final DataColumnSidecar dataColumnSidecarModified =
        sidecarSchema.create(
            builder ->
                builder
                    .index(dataColumnSidecar.getIndex())
                    .column(dataColumnSidecar.getColumn())
                    .kzgCommitments(dataColumnSidecar.getKzgCommitments())
                    .kzgProofs(dataColumnSidecar.getKzgProofs())
                    .signedBlockHeader(
                        DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader())
                    .kzgCommitmentsInclusionProof(
                        DataColumnSidecarFulu.required(dataColumnSidecar)
                            .getKzgCommitmentsInclusionProof()
                            .asListUnboxed()
                            .stream()
                            .map(Bytes32::not) // modify inclusion proof list
                            .toList()));

    final SafeFuture<?> result = listenerWrapper.onResponse(dataColumnSidecarModified);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(DataColumnSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED
                .describe());
  }

  @Test
  void dataColumnSidecarsFailsDueToSignatureVerification() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);

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
            columns);
    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);

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
