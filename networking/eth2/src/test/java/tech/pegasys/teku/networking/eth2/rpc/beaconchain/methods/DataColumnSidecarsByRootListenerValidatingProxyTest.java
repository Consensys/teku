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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Collections;
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
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@SuppressWarnings("JavaCase")
public class DataColumnSidecarsByRootListenerValidatingProxyTest {
  private final Spec spec = TestSpecFactory.createMainnetFulu();
  private final SchemaDefinitionsFulu schemaDefinitionsFulu =
      SchemaDefinitionsFulu.required(spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
  final DataColumnsByRootIdentifierSchema byRootIdentifierSchema =
      schemaDefinitionsFulu.getDataColumnsByRootIdentifierSchema();
  final DataColumnSidecarSchema sidecarSchema = schemaDefinitionsFulu.getDataColumnSidecarSchema();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private DataColumnSidecarsByRootListenerValidatingProxy listenerWrapper;
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final KZG kzg = mock(KZG.class);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final TimeProvider timeProvider = StubTimeProvider.withTimeInMillis(ZERO);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<DataColumnSidecar> listener = mock(RpcResponseListener.class);

  @BeforeEach
  void setUp() {
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(true);
  }

  @Test
  void dataColumnSidecarsAreCorrect() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(2));
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(3));
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(4));
    final List<DataColumnsByRootIdentifier> dataColumnIdentifiers =
        List.of(
            byRootIdentifierSchema.create(block1.getRoot(), List.of(ZERO, ONE)),
            byRootIdentifierSchema.create(
                block2.getRoot(), List.of(ZERO, ONE)), // ONE will be missed, shouldn't be fatal
            byRootIdentifierSchema.create(block3.getRoot(), ZERO),
            byRootIdentifierSchema.create(block4.getRoot(), ZERO));
    listenerWrapper =
        new DataColumnSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, metricsSystem, timeProvider, dataColumnIdentifiers);

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
  void blobSidecarIdentifierNotRequested() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(2));
    final List<DataColumnsByRootIdentifier> dataColumnIdentifiers =
        List.of(byRootIdentifierSchema.create(block1.getRoot(), List.of(ZERO, ONE)));
    listenerWrapper =
        new DataColumnSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, metricsSystem, timeProvider, dataColumnIdentifiers);

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
                .DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER
                .describe());
  }

  @Test
  void dataColumnSidecarFailsValidation() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final DataColumnsByRootIdentifier dataColumnIdentifier =
        byRootIdentifierSchema.create(block1.getRoot(), ZERO);
    listenerWrapper =
        new DataColumnSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, metricsSystem, timeProvider, List.of(dataColumnIdentifier));

    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(
            block1, dataColumnIdentifier.getColumns().getFirst());
    final DataColumnSidecar dataColumnSidecarModified =
        sidecarSchema.create(
            dataColumnSidecar.getIndex(),
            dataColumnSidecar.getDataColumn(),
            Collections.emptyList(), // replacing to empty commitments
            dataColumnSidecar.getSszKZGProofs().stream().map(SszKZGProof::getKZGProof).toList(),
            dataColumnSidecar.getSignedBeaconBlockHeader(),
            dataColumnSidecar.getKzgCommitmentsInclusionProof().asListUnboxed());

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
    listenerWrapper =
        new DataColumnSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, metricsSystem, timeProvider, List.of(dataColumnIdentifier));

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
    listenerWrapper =
        new DataColumnSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, metricsSystem, timeProvider, List.of(dataColumnIdentifier));

    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(
            block1, dataColumnIdentifier.getColumns().getFirst());
    final DataColumnSidecar dataColumnSidecarModified =
        sidecarSchema.create(
            dataColumnSidecar.getIndex(),
            dataColumnSidecar.getDataColumn(),
            dataColumnSidecar.getSszKZGCommitments(),
            dataColumnSidecar.getSszKZGProofs(),
            dataColumnSidecar.getSignedBeaconBlockHeader(),
            dataColumnSidecar.getKzgCommitmentsInclusionProof().asListUnboxed().stream()
                .map(Bytes32::not) // modify inclusion proof list
                .toList());

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
}
