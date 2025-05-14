/*
 * Copyright Consensys Software Inc., 2025
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.kzg.KZG.CELLS_PER_EXT_BLOB;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.fulu.BlockContentsFulu;
import tech.pegasys.teku.spec.datastructures.builder.versions.fulu.BlobsBundleFulu;
import tech.pegasys.teku.spec.datastructures.execution.BlobsCellBundle;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockFactoryFuluTest extends AbstractBlockFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldCreateBlockContents() {

    final BlobsCellBundle blobsCellBundle = prepareBlobsCellBundle(spec, 3);

    final BlockContainer blockContainer =
        assertBlockCreated(1, spec, false, state -> prepareValidPayload(spec, state), false)
            .blockContainer();

    assertThat(blockContainer).isInstanceOf(BlockContentsFulu.class);
    assertThat(blockContainer.getBlock().getBody().getOptionalBlobKzgCommitments())
        .hasValueSatisfying(blobKzgCommitments -> assertThat(blobKzgCommitments).hasSize(3));
    assertThat(blockContainer.getBlobs())
        .map(SszCollection::asList)
        .hasValue(blobsCellBundle.getBlobs());
    assertThat(blockContainer.getKzgProofs())
        .map(proofs -> proofs.stream().map(SszKZGProof::getKZGProof).toList())
        .hasValue(blobsCellBundle.getProofs());
  }

  @Test
  void shouldCreateBlindedBeaconBlockWhenBlindedBlockRequested() {

    final SszList<SszKZGCommitment> blobKzgCommitments = prepareBuilderBlobKzgCommitments(spec, 3);

    final BlockContainer blockContainer =
        assertBlockCreated(1, spec, false, state -> prepareValidPayload(spec, state), true)
            .blockContainer();

    assertThat(blockContainer).isInstanceOf(BeaconBlock.class);
    final BeaconBlock blindedBeaconBlock = (BeaconBlock) blockContainer;
    assertThat(blindedBeaconBlock.getBlock().getBody().getOptionalBlobKzgCommitments())
        .hasValue(blobKzgCommitments);
  }

  @Test
  void unblindSignedBlock_shouldPassthroughUnblindedBlock() {

    final SignedBeaconBlock signedBlock = dataStructureUtil.randomSignedBeaconBlock();

    final SignedBeaconBlock unblindedSignedBlock = assertBlockUnblinded(signedBlock, spec);

    assertThat(unblindedSignedBlock).isEqualTo(signedBlock);
  }

  @Test
  void unblindSignedBlock_shouldUnblindBeaconBlock() {

    final SignedBeaconBlock expectedUnblindedBlock = dataStructureUtil.randomSignedBeaconBlock();
    final SignedBeaconBlock blindedBlock = assertBlockBlinded(expectedUnblindedBlock, spec);

    // let the unblinder return a consistent execution payload
    executionPayload =
        expectedUnblindedBlock.getMessage().getBody().getOptionalExecutionPayload().orElseThrow();

    final SignedBeaconBlock unblindedBlock = assertBlockUnblinded(blindedBlock, spec);

    verify(executionLayer).getUnblindedPayload(unblindedBlock, BlockPublishingPerformance.NOOP);

    assertThat(unblindedBlock.isBlinded()).isFalse();
    assertThat(unblindedBlock).isEqualTo(expectedUnblindedBlock);
  }

  @Test
  void shouldCreateValidDataColumnSidecarsForBlockContents() {
    final int blobsCount = 3;
    final BlobsCellBundle blobsCellBundle = prepareBlobsCellBundle(spec, blobsCount);

    final BlockAndDataColumnSidecars blockAndDataColumnSidecars =
        createBlockAndDataColumnSidecars(false, spec);

    final List<DataColumnSidecar> dataColumnSidecars =
        blockAndDataColumnSidecars.dataColumnSidecars();

    verifyNoInteractions(executionLayer);

    final SszList<SszKZGCommitment> expectedCommitments =
        blockAndDataColumnSidecars
            .block()
            .getSignedBlock()
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow();

    assertThat(dataColumnSidecars).hasSize(CELLS_PER_EXT_BLOB);

    IntStream.range(0, dataColumnSidecars.size())
        .forEach(
            index -> {
              final DataColumnSidecar dataColumnSidecar = dataColumnSidecars.get(index);
              // check sidecar is created using the prepared BlobsCellBundle
              assertThat(
                      dataColumnSidecar.getSszKZGProofs().stream()
                          .map(SszKZGProof::getKZGProof)
                          .toList())
                  .isEqualTo(
                      IntStream.range(0, expectedCommitments.size())
                          .mapToObj(
                              blobIndex ->
                                  blobsCellBundle
                                      .getProofs()
                                      .get(blobIndex * CELLS_PER_EXT_BLOB + index))
                          .toList());
              assertThat(dataColumnSidecar.getSszKZGCommitments()).isEqualTo(expectedCommitments);
            });
  }

  @Test
  void shouldCreateValidDataColumnSidecarsForBlindedBlock() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    // random payload required to construct a valid BuilderPayload
    executionPayload = dataStructureUtil.randomExecutionPayload();

    final int blobsCount = 3;
    final BlobsBundleFulu blobsBundleFulu =
        prepareBuilderPayload(spec, blobsCount).getOptionalBlobsCellBundle().orElseThrow();

    final BlockAndDataColumnSidecars blockAndDataColumnSidecars =
        createBlockAndDataColumnSidecars(true, spec);

    final SignedBlockContainer block = blockAndDataColumnSidecars.block();
    final List<DataColumnSidecar> dataColumnSidecars =
        blockAndDataColumnSidecars.dataColumnSidecars();

    verify(executionLayer).getCachedUnblindedPayload(block.getSlot());

    final SszList<SszKZGCommitment> expectedCommitments =
        block.getSignedBlock().getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();

    assertThat(dataColumnSidecars).hasSize(CELLS_PER_EXT_BLOB);

    IntStream.range(0, dataColumnSidecars.size())
        .forEach(
            index -> {
              final DataColumnSidecar dataColumnSidecar = dataColumnSidecars.get(index);
              // check sidecar is created using the prepared BlobsCellBundle
              assertThat(dataColumnSidecar.getSszKZGProofs().asList())
                  .isEqualTo(
                      IntStream.range(0, expectedCommitments.size())
                          .mapToObj(
                              blobIndex ->
                                  blobsBundleFulu
                                      .getProofs()
                                      .get(blobIndex * CELLS_PER_EXT_BLOB + index))
                          .toList());
              assertThat(dataColumnSidecar.getSszKZGCommitments()).isEqualTo(expectedCommitments);
            });
  }

  @Override
  public BlockFactory createBlockFactory(final Spec spec) {
    final KZG kzg = mock(KZG.class);
    when(kzg.computeCells(any()))
        .thenReturn(
            IntStream.range(0, 128).mapToObj(__ -> dataStructureUtil.randomKZGCell()).toList());
    return new BlockFactoryFulu(
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
            graffitiBuilder,
            forkChoiceNotifier,
            executionLayer,
            metricsSystem,
            timeProvider),
        kzg);
  }
}
