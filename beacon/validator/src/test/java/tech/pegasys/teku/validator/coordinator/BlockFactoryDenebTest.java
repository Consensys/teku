/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarOld;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockFactoryDenebTest extends AbstractBlockFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaDefinitionsDeneb schemaDefinitions =
      SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions());

  @Test
  void shouldCreateBlockContents() {

    final BlobsBundle blobsBundle = prepareBlobsBundle(spec, 3);

    final BlockContainer blockContainer =
        assertBlockCreated(1, spec, false, state -> prepareValidPayload(spec, state), false);

    assertThat(blockContainer).isInstanceOf(BlockContents.class);
    assertThat(blockContainer.getBlock().getBody().getOptionalBlobKzgCommitments())
        .hasValueSatisfying(blobKzgCommitments -> assertThat(blobKzgCommitments).hasSize(3));
    assertThat(blockContainer.getBlobSidecars())
        .hasValueSatisfying(
            blobSidecars ->
                assertThat(blobSidecars)
                    .hasSize(3)
                    .map(BlobSidecarOld::getBlob)
                    .hasSameElementsAs(blobsBundle.getBlobs()));
  }

  @Test
  void shouldCreateBlindedBeaconBlockWhenBlindedBlockRequested() {

    final SszList<SszKZGCommitment> blobKzgCommitments = prepareBlobKzgCommitments(spec, 3);

    final BlockContainer blockContainer =
        assertBlockCreated(1, spec, false, state -> prepareValidPayload(spec, state), true);

    assertThat(blockContainer).isInstanceOf(BeaconBlock.class);
    final BeaconBlock blindedBeaconBlock = (BeaconBlock) blockContainer;
    assertThat(blindedBeaconBlock.getBlock().getBody().getOptionalBlobKzgCommitments())
        .hasValue(blobKzgCommitments);
  }

  @Test
  void unblindSignedBlock_shouldPassthroughUnblindedBlockContents() {

    final SignedBlockContents signedBlockContents = dataStructureUtil.randomSignedBlockContents();

    final SignedBlockContainer unblindedSignedBlockContainer =
        assertBlockUnblinded(signedBlockContents, spec);

    assertThat(unblindedSignedBlockContainer).isEqualTo(signedBlockContents);
  }

  @Test
  @Disabled(
      "enable when block production flow for blob sidecar inclusion proof spec is implemented")
  void unblindSignedBlock_shouldUnblindBeaconBlock() {

    final BlobsBundle blobsBundle = prepareBlobsBundle(spec, 3);
    // let the unblinder verify the kzg commitments
    blobKzgCommitments =
        Optional.of(
            schemaDefinitions.getBlobKzgCommitmentsSchema().createFromBlobsBundle(blobsBundle));

    final SignedBeaconBlock unblindedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
    final SignedBeaconBlock blindedBlock = assertBlockBlinded(unblindedBeaconBlock, spec);

    // let the unblinder return a consistent execution payload
    executionPayload =
        unblindedBeaconBlock.getMessage().getBody().getOptionalExecutionPayload().orElseThrow();

    final SignedBlockContainer unblindedBlockContainer = assertBlockUnblinded(blindedBlock, spec);

    // make sure getCachedUnblindedPayload is second in order of method calling
    final InOrder inOrder = Mockito.inOrder(executionLayer);
    inOrder.verify(executionLayer).getUnblindedPayload(unblindedBlockContainer);
    inOrder.verify(executionLayer).getCachedUnblindedPayload(unblindedBlockContainer.getSlot());

    assertThat(unblindedBlockContainer).isInstanceOf(SignedBlockContents.class);
    assertThat(unblindedBlockContainer.isBlinded()).isFalse();
    assertThat(unblindedBlockContainer.getSignedBlock()).isEqualTo(unblindedBeaconBlock);
    assertThat(unblindedBlockContainer.getSignedBlobSidecars())
        .hasValueSatisfying(blobSidecars -> assertThat(blobSidecars).isEmpty());
  }

  @Override
  public BlockFactory createBlockFactory(final Spec spec) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final Bytes32 graffiti = dataStructureUtil.randomBytes32();
    return new BlockFactoryDeneb(
        spec,
        new BlockOperationSelectorFactory(
            spec,
            attestationsPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool,
            blsToExecutionChangePool,
            syncCommitteeContributionPool,
            depositProvider,
            eth1DataCache,
            graffiti,
            forkChoiceNotifier,
            executionLayer));
  }
}
