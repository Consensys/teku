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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.gloas.DataColumnSidecarSchemaGloas;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

@SuppressWarnings("JavaCase")
public class DataColumnSidecarsByRootListenerValidatingProxyGloasTest
    extends AbstractDataColumnSidecarsByRootListenerValidatingProxyTest {

  private final Map<Bytes32, SignedBeaconBlock> blocksByRoot = new HashMap<>();

  @BeforeEach
  @Override
  void setUp() {
    super.setUp();
    blocksByRoot.clear();
    // For Gloas, set up mock to retrieve blocks by root
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenAnswer(
            invocation -> {
              final Bytes32 root = invocation.getArgument(0);
              return SafeFuture.completedFuture(Optional.ofNullable(blocksByRoot.get(root)));
            });
  }

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetGloas();
  }

  @Override
  protected SignedBeaconBlock createBlock(final UInt64 slot) {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(slot, 3);
    blocksByRoot.put(block.getRoot(), block);
    return block;
  }

  @Override
  protected List<DataColumnsByRootIdentifier> createDataColumnIdentifiers(
      final List<SignedBeaconBlock> blocks, final List<List<UInt64>> columnIndices) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(
            spec.forMilestone(SpecMilestone.GLOAS).getSchemaDefinitions());
    final DataColumnsByRootIdentifierSchema byRootIdentifierSchema =
        schemaDefinitions.getDataColumnsByRootIdentifierSchema();

    final List<DataColumnsByRootIdentifier> identifiers = new ArrayList<>();
    for (int i = 0; i < blocks.size(); i++) {
      identifiers.add(byRootIdentifierSchema.create(blocks.get(i).getRoot(), columnIndices.get(i)));
    }
    return identifiers;
  }

  @Override
  protected DataColumnSidecar createSidecarWithInvalidStructure(
      final DataColumnSidecar validSidecar) {
    final DataColumnSidecarSchemaGloas sidecarSchema =
        (DataColumnSidecarSchemaGloas) validSidecar.getSchema();
    final DataColumnSchema dataColumnSchema =
        SchemaDefinitionsGloas.required(spec.atSlot(validSidecar.getSlot()).getSchemaDefinitions())
            .getDataColumnSchema();
    return sidecarSchema.create(
        builder ->
            builder
                .index(validSidecar.getIndex())
                .column(dataColumnSchema.create(List.of()))
                .kzgProofs(validSidecar.getKzgProofs().getSchema().createFromElements(List.of()))
                .slot(validSidecar.getSlot())
                .beaconBlockRoot(validSidecar.getBeaconBlockRoot()));
  }

  @Override
  @Test
  void dataColumnSidecarFailsKzgVerification() {
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(false);
    final SignedBeaconBlock block1 = createBlock(ONE);
    final List<DataColumnsByRootIdentifier> dataColumnIdentifiers =
        createDataColumnIdentifiers(List.of(block1), List.of(List.of(ZERO)));

    listenerWrapper =
        new DataColumnSidecarsByRootListenerValidatingProxy(
            peer,
            spec,
            listener,
            metricsSystem,
            timeProvider,
            signatureValidator,
            dataColumnIdentifiers,
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
}
