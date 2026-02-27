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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

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
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.gloas.DataColumnSidecarSchemaGloas;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

@SuppressWarnings("JavaCase")
public class DataColumnSidecarsByRootValidatorGloasTest
    extends AbstractDataColumnSidecarsByRootValidatorTest {

  private final Map<Bytes32, SignedBeaconBlock> blocksByRoot = new HashMap<>();

  @BeforeEach
  @Override
  void setUp() {
    super.setUp();
    blocksByRoot.clear();
    // For Gloas, validateWithBlock fetches the block to retrieve bid KZG commitments
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenAnswer(
            invocation -> {
              final Bytes32 root = invocation.getArgument(0);
              return SafeFuture.completedFuture(Optional.ofNullable(blocksByRoot.get(root)));
            });
  }

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalWithGloasForkEpoch(currentForkEpoch);
  }

  @Override
  protected SignedBeaconBlock createBlock(final UInt64 slot) {
    // Create a block with commitments and register it so validateWithBlock can find it
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(slot, 3);
    blocksByRoot.put(block.getRoot(), block);
    return block;
  }

  @Override
  protected DataColumnSidecar createSidecarWithBrokenValidity(final DataColumnSidecar sidecar) {
    final DataColumnSidecarSchemaGloas sidecarSchema =
        (DataColumnSidecarSchemaGloas) sidecar.getSchema();
    final DataColumnSchema dataColumnSchema =
        SchemaDefinitionsGloas.required(spec.atSlot(sidecar.getSlot()).getSchemaDefinitions())
            .getDataColumnSchema();
    return sidecarSchema.create(
        builder ->
            builder
                .index(sidecar.getIndex())
                .column(dataColumnSchema.create(List.of()))
                .kzgProofs(sidecar.getKzgProofs().getSchema().createFromElements(List.of()))
                .slot(sidecar.getSlot())
                .beaconBlockRoot(sidecar.getBeaconBlockRoot()));
  }

  @Override
  @Test
  void dataColumnSidecarFailsKzgVerification() {
    // In Gloas, KZG verification uses commitments from the block's execution payload bid
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(false);

    final SignedBeaconBlock block1 = createBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecar(block1, UInt64.ZERO);
    final DataColumnIdentifier sidecarIdentifier1_0 =
        DataColumnIdentifier.createFromSidecar(dataColumnSidecar1_0);
    validator =
        new DataColumnSidecarsByRootValidator(
            peer,
            spec,
            metricsSystem,
            timeProvider,
            dataColumnSidecarSignatureValidator,
            List.of(sidecarIdentifier1_0),
            combinedChainDataClient);

    assertThatSafeFuture(validator.validate(dataColumnSidecar1_0))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED
                .describe());
  }
}
