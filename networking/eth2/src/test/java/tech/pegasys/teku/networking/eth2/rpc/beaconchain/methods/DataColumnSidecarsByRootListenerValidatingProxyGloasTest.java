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
    // Blocks will be registered as they're created in tests
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
    // For Gloas, we need to create blocks with a specific number of commitments
    // Use a moderate number that's typical for tests (e.g., 3 blobs)
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(slot, 3);
    // Register block so it can be retrieved by the mock
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

    // Create an invalid sidecar by making wrong number of proofs (skip first proof)
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
    // Gloas defers KZG verification (always returns true), so this test doesn't apply
    // KZG verification for Gloas happens during validateWithBlock, not as a separate step
  }
}
