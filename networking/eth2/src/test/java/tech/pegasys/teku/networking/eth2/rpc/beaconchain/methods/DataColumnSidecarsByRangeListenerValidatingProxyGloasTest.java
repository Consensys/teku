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
import tech.pegasys.teku.spec.datastructures.blobs.versions.gloas.DataColumnSidecarSchemaGloas;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class DataColumnSidecarsByRangeListenerValidatingProxyGloasTest
    extends AbstractDataColumnSidecarsByRangeListenerValidatingProxyTest {

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
  protected DataColumnSidecar createSidecarWithInvalidStructure(
      final DataColumnSidecar validSidecar) {
    final DataColumnSidecarSchemaGloas sidecarSchema =
        (DataColumnSidecarSchemaGloas) validSidecar.getSchema();
    // invalid sidecar with wrong number of proofs (skip first proof)
    return sidecarSchema.create(
        builder ->
            builder
                .index(validSidecar.getIndex())
                .column(validSidecar.getColumn())
                .kzgProofs(
                    validSidecar
                        .getKzgProofs()
                        .getSchema()
                        .createFromElements(validSidecar.getKzgProofs().stream().skip(1).toList()))
                .slot(validSidecar.getSlot())
                .beaconBlockRoot(validSidecar.getBeaconBlockRoot()));
  }

  @Override
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
                .DATA_COLUMN_SIDECAR_VALIDITY_CHECK_FAILED
                .describe());
  }
}
