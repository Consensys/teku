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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@SuppressWarnings("JavaCase")
public class DataColumnSidecarsByRootListenerValidatingProxyTest {
  private final Spec spec = TestSpecFactory.createMainnetFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private DataColumnSidecarsByRootListenerValidatingProxy listenerWrapper;
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final KZG kzg = mock(KZG.class);
  private final MetricsSystem metricsSystem = mock(MetricsSystem.class);
  private final TimeProvider timeProvider = mock(TimeProvider.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<DataColumnSidecar> listener = mock(RpcResponseListener.class);

  @BeforeEach
  void setUp() {
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(true);
  }

  @Test
  void dataColumnSidecarsAreCorrect() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(2));
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(3));
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(4));
    final List<DataColumnIdentifier> dataColumnIdentifiers =
        List.of(
            new DataColumnIdentifier(block1.getRoot(), UInt64.ZERO),
            new DataColumnIdentifier(block1.getRoot(), UInt64.ONE),
            new DataColumnIdentifier(block2.getRoot(), UInt64.ZERO),
            new DataColumnIdentifier(
                block2.getRoot(), UInt64.ONE), // will be missed, shouldn't be fatal
            new DataColumnIdentifier(block3.getRoot(), UInt64.ZERO),
            new DataColumnIdentifier(block4.getRoot(), UInt64.ZERO));
    listenerWrapper =
        new DataColumnSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, metricsSystem, timeProvider, dataColumnIdentifiers);

    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, UInt64.ZERO);
    final DataColumnSidecar dataColumnSidecar1_1 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, UInt64.ONE);
    final DataColumnSidecar dataColumnSidecar2_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block2, UInt64.ZERO);
    final DataColumnSidecar dataColumnSidecar3_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block3, UInt64.ZERO);
    final DataColumnSidecar dataColumnSidecar4_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block4, UInt64.ZERO);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(dataColumnSidecar1_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(dataColumnSidecar1_1).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(dataColumnSidecar2_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(dataColumnSidecar3_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(dataColumnSidecar4_0).join());
  }

  @Test
  void blobSidecarIdentifierNotRequested() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(2));
    final List<DataColumnIdentifier> dataColumnIdentifiers =
        List.of(
            new DataColumnIdentifier(block1.getRoot(), UInt64.ZERO),
            new DataColumnIdentifier(block1.getRoot(), UInt64.ONE));
    listenerWrapper =
        new DataColumnSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, metricsSystem, timeProvider, dataColumnIdentifiers);

    final DataColumnSidecar datColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, UInt64.ZERO);
    final DataColumnSidecar datColumnSidecar1_1 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, UInt64.ONE);
    final DataColumnSidecar datColumnSidecar2_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block2, UInt64.ZERO);

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
  void dataColumnSidecarFailsKzgVerification() {
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(false);
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final DataColumnIdentifier dataColumnIdentifier =
        new DataColumnIdentifier(block1.getRoot(), UInt64.ZERO);
    listenerWrapper =
        new DataColumnSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, metricsSystem, timeProvider, List.of(dataColumnIdentifier));

    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(
            block1, dataColumnIdentifier.getIndex());

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
    final SchemaDefinitionsElectra schemaDefinitionsElectra =
        SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions());
    final DataColumnSidecar dataColumnSidecar =
        SchemaDefinitionsFulu.required(schemaDefinitionsElectra)
            .getDataColumnSidecarSchema()
            .create(
                UInt64.ZERO,
                SchemaDefinitionsFulu.required(schemaDefinitionsElectra)
                    .getDataColumnSchema()
                    .create(List.of()),
                List.of(),
                List.of(),
                new SignedBeaconBlockHeader(
                    new BeaconBlockHeader(
                        UInt64.valueOf(37),
                        UInt64.valueOf(3426),
                        Bytes32.fromHexString(
                            "0x6d3091dae0e2a0251cc2c0d9fef846e1c6e685f18fc8a2c7734f25750c22da36"),
                        Bytes32.fromHexString(
                            "0x715f24108254c3fcbef60c739fe702aed3ee692cb223c884b3db6e041c56c2a6"),
                        Bytes32.fromHexString(
                            "0xbea87258cde49915c8c929b6b91fbbcde004aeaaa08a3ccdc3248dc62b0e682f")),
                    BLSSignature.fromBytesCompressed(
                        Bytes.fromHexString(
                            "0xb4c313365edbc7cfa9319c54ecba0a8dc54c8537752c72a86c762eb0a81b3ad1eda43f0f3b19a9c9523a6a42450c1d070556e0a443d4733922765764ef5850b41d20b4f6af6cc93a70eb1023cc63473f111de772315a2726406be9dc6cb24e67"))),
                List.of(
                    Bytes32.fromHexString(
                        "0x792930bbd5baac43bcc798ee49aa8185ef76bb3b44ba62b91d86ae569e4bb535"),
                    Bytes32.fromHexString(
                        "0xcd581849371d5f91b7d02a366b23402397007b50180069584f2bd4e14397540b"),
                    Bytes32.fromHexString(
                        "0xdb56114e00fdd4c1f85c892bf35ac9a89289aaecb1ebd0a96cde606a748b5d71"),
                    Bytes32.fromHexString(
                        "0x9535c3eb42aaf182b13b18aacbcbc1df6593ecafd0bf7d5e94fb727b2dc1f265")));
    final DataColumnIdentifier dataColumnIdentifier =
        new DataColumnIdentifier(dataColumnSidecar.getBlockRoot(), UInt64.ZERO);
    listenerWrapper =
        new DataColumnSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, kzg, metricsSystem, timeProvider, List.of(dataColumnIdentifier));

    final SafeFuture<?> result = listenerWrapper.onResponse(dataColumnSidecar);
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
