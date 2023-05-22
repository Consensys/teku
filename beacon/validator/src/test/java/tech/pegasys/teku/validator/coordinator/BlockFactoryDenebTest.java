/*
 * Copyright ConsenSys Software Inc., 2023
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

import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.blocks.BlindedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockFactoryDenebTest extends AbstractBlockFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();

  @Test
  void shouldCreateBlockContentsWhenDenebIsActive() {

    prepareDefaultPayload(spec);
    final BlobsBundle blobsBundle = prepareBlobsBundle(spec, 3);

    final BlockContainer blockContainer = assertBlockCreated(1, spec, false, false);

    assertThat(blockContainer).isInstanceOf(BlockContents.class);
    assertThat(blockContainer.getBlobSidecars())
        .hasValueSatisfying(
            blobSidecars ->
                assertThat(blobSidecars)
                    .hasSize(3)
                    .map(BlobSidecar::getBlob)
                    .hasSameElementsAs(blobsBundle.getBlobs()));
  }

  @Test
  void shouldCreateBlindedBlockContentsWhenDenebIsActiveAndBlindedBlockRequested() {

    prepareDefaultPayload(spec);
    final BlobsBundle blobsBundle = prepareBlobsBundle(spec, 3);

    final BlockContainer blockContainer = assertBlockCreated(1, spec, false, true);

    assertThat(blockContainer).isInstanceOf(BlindedBlockContents.class);
    final BlindedBlockContainer blindedBlockContainer = blockContainer.toBlinded().orElseThrow();
    assertThat(blindedBlockContainer.getBlindedBlobSidecars())
        .hasValueSatisfying(
            blindedBlobSidecars ->
                assertThat(blindedBlobSidecars)
                    .hasSize(3)
                    .map(BlindedBlobSidecar::getBlobRoot)
                    .hasSameElementsAs(
                        blobsBundle.getBlobs().stream()
                            .map(Blob::hashTreeRoot)
                            .collect(Collectors.toList())));
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
