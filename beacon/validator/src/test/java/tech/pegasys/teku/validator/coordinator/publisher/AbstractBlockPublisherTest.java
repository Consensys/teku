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

package tech.pegasys.teku.validator.coordinator.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

public class AbstractBlockPublisherTest {
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BlockFactory blockFactory = mock(BlockFactory.class);
  private final BlockGossipChannel blockGossipChannel = mock(BlockGossipChannel.class);
  private final BlockImportChannel blockImportChannel = mock(BlockImportChannel.class);
  private final DutyMetrics dutyMetrics = mock(DutyMetrics.class);

  private final AbstractBlockPublisher blockPublisher =
      spy(
          new BlockPublisherTest(
              asyncRunner,
              blockFactory,
              blockGossipChannel,
              blockImportChannel,
              dutyMetrics,
              false));

  final SignedBlockContainer signedBlockContents = dataStructureUtil.randomSignedBlockContents();
  final SignedBeaconBlock signedBlock = signedBlockContents.getSignedBlock();
  final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(signedBlock);

  @BeforeEach
  public void setUp() {
    when(blockPublisher.publishBlock(any(), any())).thenReturn(SafeFuture.COMPLETE);
    when(blockFactory.unblindSignedBlockIfBlinded(signedBlock, BlockPublishingPerformance.NOOP))
        .thenReturn(SafeFuture.completedFuture(Optional.of(signedBlock)));
    when(blockFactory.createBlobSidecars(signedBlockContents)).thenReturn(blobSidecars);
  }

  @Test
  public void
      sendSignedBlock_shouldPublishImmediatelyAndImportWhenBroadcastValidationIsNotRequired() {

    when(blockPublisher.importBlock(
            signedBlock, BroadcastValidationLevel.NOT_REQUIRED, BlockPublishingPerformance.NOOP))
        .thenReturn(prepareBlockImportResult(BlockImportResult.successful(signedBlock)));

    assertThatSafeFuture(
            blockPublisher.sendSignedBlock(
                signedBlockContents,
                BroadcastValidationLevel.NOT_REQUIRED,
                BlockPublishingPerformance.NOOP))
        .isCompletedWithValue(SendSignedBlockResult.success(signedBlockContents.getRoot()));

    verify(blockPublisher).publishBlock(signedBlock, BlockPublishingPerformance.NOOP);
    verify(blockPublisher).publishBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);
    verify(blockPublisher)
        .importBlock(
            signedBlock, BroadcastValidationLevel.NOT_REQUIRED, BlockPublishingPerformance.NOOP);
    verify(blockPublisher).importBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);
  }

  @Test
  public void sendSignedBlock_shouldWaitToPublishWhenBroadcastValidationIsSpecified() {
    final SafeFuture<BroadcastValidationResult> validationResult = new SafeFuture<>();
    when(blockPublisher.importBlock(
            signedBlock,
            BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION,
            BlockPublishingPerformance.NOOP))
        .thenReturn(
            prepareBlockImportResult(BlockImportResult.successful(signedBlock), validationResult));

    final SafeFuture<SendSignedBlockResult> sendSignedBlockResult =
        blockPublisher.sendSignedBlock(
            signedBlockContents,
            BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION,
            BlockPublishingPerformance.NOOP);

    assertThatSafeFuture(sendSignedBlockResult).isNotCompleted();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    verify(blockPublisher, never()).importBlobSidecars(any(), any());
    asyncRunner.executeDueActions();
    verify(blockPublisher).importBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);

    verify(blockPublisher)
        .importBlock(
            signedBlock,
            BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION,
            BlockPublishingPerformance.NOOP);

    verify(blockPublisher, never()).publishBlock(signedBlock, BlockPublishingPerformance.NOOP);
    verify(blockPublisher, never())
        .publishBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);

    validationResult.complete(BroadcastValidationResult.SUCCESS);

    verify(blockPublisher).publishBlock(signedBlock, BlockPublishingPerformance.NOOP);
    verify(blockPublisher).publishBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);
    assertThatSafeFuture(sendSignedBlockResult)
        .isCompletedWithValue(SendSignedBlockResult.success(signedBlockContents.getRoot()));
  }

  @Test
  public void sendSignedBlock_shouldNotPublishWhenBroadcastValidationFails() {
    final SafeFuture<BroadcastValidationResult> validationResult = new SafeFuture<>();
    when(blockPublisher.importBlock(
            signedBlock,
            BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION,
            BlockPublishingPerformance.NOOP))
        .thenReturn(
            SafeFuture.completedFuture(
                new BlockImportAndBroadcastValidationResults(
                    SafeFuture.completedFuture(BlockImportResult.successful(signedBlock)),
                    validationResult)));

    final SafeFuture<SendSignedBlockResult> sendSignedBlockResult =
        blockPublisher.sendSignedBlock(
            signedBlockContents,
            BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION,
            BlockPublishingPerformance.NOOP);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    assertThatSafeFuture(sendSignedBlockResult).isNotCompleted();

    verify(blockPublisher)
        .importBlock(
            signedBlock,
            BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION,
            BlockPublishingPerformance.NOOP);

    verify(blockPublisher, never()).publishBlock(signedBlock, BlockPublishingPerformance.NOOP);
    verify(blockPublisher, never())
        .publishBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);

    validationResult.complete(BroadcastValidationResult.CONSENSUS_FAILURE);

    verify(blockPublisher, never()).publishBlock(signedBlock, BlockPublishingPerformance.NOOP);
    verify(blockPublisher, never())
        .publishBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);

    assertThatSafeFuture(sendSignedBlockResult)
        .isCompletedWithValue(
            SendSignedBlockResult.rejected("FAILED_BROADCAST_VALIDATION: CONSENSUS_FAILURE"));
  }

  @Test
  public void sendSignedBlock_shouldPublishBlobsAfterBlockWhenOptionIsEnabled() {
    final AbstractBlockPublisher blockPublisher =
        spy(
            new BlockPublisherTest(
                asyncRunner,
                blockFactory,
                blockGossipChannel,
                blockImportChannel,
                dutyMetrics,
                true));

    SafeFuture<Void> publishBlockFuture = new SafeFuture<>();
    when(blockPublisher.publishBlock(any(), any())).thenReturn(publishBlockFuture);

    when(blockPublisher.importBlock(
            signedBlock, BroadcastValidationLevel.NOT_REQUIRED, BlockPublishingPerformance.NOOP))
        .thenReturn(prepareBlockImportResult(BlockImportResult.successful(signedBlock)));

    assertThatSafeFuture(
            blockPublisher.sendSignedBlock(
                signedBlockContents,
                BroadcastValidationLevel.NOT_REQUIRED,
                BlockPublishingPerformance.NOOP))
        .isCompletedWithValue(SendSignedBlockResult.success(signedBlockContents.getRoot()));

    verify(blockPublisher).publishBlock(signedBlock, BlockPublishingPerformance.NOOP);
    verify(blockPublisher, never())
        .publishBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);

    // Complete block publishing
    publishBlockFuture.complete(null);

    verify(blockPublisher).publishBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);

    verify(blockPublisher)
        .importBlock(
            signedBlock, BroadcastValidationLevel.NOT_REQUIRED, BlockPublishingPerformance.NOOP);
    verify(blockPublisher).importBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);
  }

  @Test
  public void sendSignedBlock_shouldReturnNotImportedWhenBlockImportFails() {
    when(blockPublisher.importBlock(
            signedBlock, BroadcastValidationLevel.NOT_REQUIRED, BlockPublishingPerformance.NOOP))
        .thenReturn(
            prepareBlockImportResult(
                BlockImportResult.failedStateTransition(new RuntimeException("Failed"))));

    assertThatSafeFuture(
            blockPublisher.sendSignedBlock(
                signedBlockContents,
                BroadcastValidationLevel.NOT_REQUIRED,
                BlockPublishingPerformance.NOOP))
        .isCompletedWithValue(
            SendSignedBlockResult.notImported(FailureReason.FAILED_STATE_TRANSITION.name()));

    verify(blockPublisher).publishBlock(signedBlock, BlockPublishingPerformance.NOOP);
    verify(blockPublisher).publishBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);
    verify(blockPublisher)
        .importBlock(
            signedBlock, BroadcastValidationLevel.NOT_REQUIRED, BlockPublishingPerformance.NOOP);
    verify(blockPublisher).importBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);
  }

  private SafeFuture<BlockImportAndBroadcastValidationResults> prepareBlockImportResult(
      final BlockImportResult blockImportResult) {
    return SafeFuture.completedFuture(
        new BlockImportAndBroadcastValidationResults(
            SafeFuture.completedFuture(blockImportResult)));
  }

  private SafeFuture<BlockImportAndBroadcastValidationResults> prepareBlockImportResult(
      final BlockImportResult blockImportResult,
      final SafeFuture<BroadcastValidationResult> broadcastValidationResult) {
    return SafeFuture.completedFuture(
        new BlockImportAndBroadcastValidationResults(
            SafeFuture.completedFuture(blockImportResult), broadcastValidationResult));
  }

  private static class BlockPublisherTest extends AbstractBlockPublisher {
    public BlockPublisherTest(
        final AsyncRunner asyncRunner,
        final BlockFactory blockFactory,
        final BlockGossipChannel blockGossipChannel,
        final BlockImportChannel blockImportChannel,
        final DutyMetrics dutyMetrics,
        final boolean gossipBlobsAfterBlock) {
      super(
          asyncRunner,
          blockFactory,
          blockGossipChannel,
          blockImportChannel,
          dutyMetrics,
          gossipBlobsAfterBlock);
    }

    @Override
    SafeFuture<BlockImportAndBroadcastValidationResults> importBlock(
        final SignedBeaconBlock block,
        final BroadcastValidationLevel broadcastValidationLevel,
        final BlockPublishingPerformance blockPublishingPerformance) {
      return null;
    }

    @Override
    void importBlobSidecars(
        final List<BlobSidecar> blobSidecars,
        final BlockPublishingPerformance blockPublishingPerformance) {}

    @Override
    SafeFuture<Void> publishBlock(
        final SignedBeaconBlock block,
        final BlockPublishingPerformance blockPublishingPerformance) {
      return null;
    }

    @Override
    void publishBlobSidecars(
        final List<BlobSidecar> blobSidecars,
        final BlockPublishingPerformance blockPublishingPerformance) {}

    @Override
    void publishDataColumnSidecars(
        final List<DataColumnSidecar> dataColumnSidecars,
        final BlockPublishingPerformance blockPublishingPerformance) {}
  }
}
