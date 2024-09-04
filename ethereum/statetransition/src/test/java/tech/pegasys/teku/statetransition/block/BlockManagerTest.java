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

package tech.pegasys.teku.statetransition.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.GOSSIP;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason.FAILED_DATA_AVAILABILITY_CHECK_INVALID;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason.FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason.UNKNOWN_PARENT;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.ARRIVAL_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.BEGIN_IMPORTING_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.COMPLETED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.DATA_AVAILABILITY_CHECKED_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.EXECUTION_PAYLOAD_RESULT_RECEIVED_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.GOSSIP_VALIDATION_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.PRESTATE_RETRIEVED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.PROCESSED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.TRANSACTION_COMMITTED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.TRANSACTION_PREPARED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult.SUCCESS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityFactory;

@SuppressWarnings("FutureReturnValueIgnored")
public class BlockManagerTest {
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final EventLogger eventLogger = mock(EventLogger.class);
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher =
      mock(ReceivedBlockEventsChannel.class);
  private final UInt64 historicalBlockTolerance = UInt64.valueOf(5);
  private final UInt64 futureBlockTolerance = UInt64.valueOf(2);
  private final int maxPendingBlocks = 10;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool =
      mock(BlockBlobSidecarsTrackersPool.class);
  private PendingPool<SignedBeaconBlock> pendingBlocks;
  private final FutureItems<SignedBeaconBlock> futureBlocks =
      FutureItems.create(SignedBeaconBlock::getSlot, mock(SettableLabelledGauge.class), "blocks");
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots =
      LimitedMap.createSynchronizedLRU(500);

  private StorageSystem localChain;
  private RecentChainData localRecentChainData = mock(RecentChainData.class);

  private final ForkChoiceNotifier forkChoiceNotifier = new NoopForkChoiceNotifier();
  private MergeTransitionBlockValidator transitionBlockValidator;
  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);
  private ForkChoice forkChoice;

  private ExecutionLayerChannelStub executionLayer;
  private final BlockValidator blockValidator = mock(BlockValidator.class);
  private final BlockBroadcastValidator blockBroadcastValidator =
      mock(BlockBroadcastValidator.class);

  private BlockImporter blockImporter;
  private BlockManager blockManager;

  private UInt64 currentSlot = GENESIS_SLOT;

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.depositSignatureVerifier = BLSSignatureVerifier.NO_OP;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.depositSignatureVerifier =
        AbstractBlockProcessor.DEFAULT_DEPOSIT_SIGNATURE_VERIFIER;
  }

  @BeforeEach
  public void setup() {
    setupWithSpec(TestSpecFactory.createMinimalDeneb());
  }

  private void setupWithSpec(final Spec spec) {
    this.spec = spec;
    this.dataStructureUtil = new DataStructureUtil(spec);
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();
    this.pendingBlocks =
        new PoolFactory(metricsSystem)
            .createPendingPoolForBlocks(
                spec, historicalBlockTolerance, futureBlockTolerance, maxPendingBlocks);
    this.localChain = InMemoryStorageSystemBuilder.buildDefault(spec);
    this.localRecentChainData = localChain.recentChainData();
    this.transitionBlockValidator =
        new MergeTransitionBlockValidator(spec, localRecentChainData, ExecutionLayerChannel.NOOP);
    this.forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            localRecentChainData,
            blobSidecarManager,
            forkChoiceNotifier,
            transitionBlockValidator,
            metricsSystem);
    this.executionLayer = spy(new ExecutionLayerChannelStub(spec, false, Optional.empty()));
    this.blockImporter =
        new BlockImporter(
            spec,
            receivedBlockEventsChannelPublisher,
            localRecentChainData,
            forkChoice,
            WeakSubjectivityFactory.lenientValidator(),
            executionLayer);
    this.blockManager =
        new BlockManager(
            localRecentChainData,
            blockImporter,
            blockBlobSidecarsTrackersPool,
            pendingBlocks,
            futureBlocks,
            invalidBlockRoots,
            blockValidator,
            timeProvider,
            eventLogger,
            Optional.of(mock(BlockImportMetrics.class)));
    forwardBlockImportedNotificationsTo(blockManager);
    localChain
        .chainUpdater()
        .initializeGenesisWithPayload(false, dataStructureUtil.randomExecutionPayloadHeader());
    assertThat(blockManager.start()).isCompleted();
    when(blobSidecarManager.createAvailabilityChecker(any()))
        .thenReturn(AvailabilityChecker.NOOP_BLOBSIDECAR);
    when(blockValidator.initiateBroadcastValidation(any(), any()))
        .thenReturn(blockBroadcastValidator);
    when(blockBroadcastValidator.getResult()).thenReturn(SafeFuture.completedFuture(SUCCESS));
  }

  private void forwardBlockImportedNotificationsTo(final BlockManager blockManager) {
    doAnswer(
            invocation -> {
              blockManager.onBlockImported(invocation.getArgument(0), invocation.getArgument(1));
              return null;
            })
        .when(receivedBlockEventsChannelPublisher)
        .onBlockImported(any(), anyBoolean());
  }

  @AfterEach
  public void cleanup() {
    assertThat(blockManager.stop()).isCompleted();
    reset(blobSidecarManager);
  }

  @Test
  public void shouldImport() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    incrementSlot();

    safeJoinBlockImport(nextBlock);
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void shouldBeNotifiedOnImport() {
    final RecentChainData localRecentChainData = mock(RecentChainData.class);
    blockManager = setupBlockManagerWithMockRecentChainData(localRecentChainData, false);

    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    incrementSlot();

    safeJoinBlockImport(nextBlock);
    verify(receivedBlockEventsChannelPublisher).onBlockImported(nextBlock, false);
    verify(blockBlobSidecarsTrackersPool).removeAllForBlock(nextBlock.getRoot());
  }

  @Test
  public void shouldBeNotNotifiedOnKnownBlock() {
    final RecentChainData localRecentChainData = mock(RecentChainData.class);
    blockManager = setupBlockManagerWithMockRecentChainData(localRecentChainData, false);

    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    incrementSlot();

    safeJoinBlockImport(nextBlock);
    verify(receivedBlockEventsChannelPublisher).onBlockImported(nextBlock, false);

    // should not be notified
    assertThatBlockImport(nextBlock)
        .isCompletedWithValue(BlockImportResult.knownBlock(nextBlock, false));
    verifyNoMoreInteractions(receivedBlockEventsChannelPublisher);
  }

  @Test
  public void shouldBeNotNotifiedOnKnownOptimisticBlock() {
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);
    final RecentChainData localRecentChainData = mock(RecentChainData.class);
    blockManager = setupBlockManagerWithMockRecentChainData(localRecentChainData, true);
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    incrementSlot();

    safeJoinBlockImport(nextBlock);
    verify(receivedBlockEventsChannelPublisher).onBlockImported(nextBlock, true);

    // should not be notified
    assertThatBlockImport(nextBlock)
        .isCompletedWithValue(BlockImportResult.knownBlock(nextBlock, true));
    verifyNoMoreInteractions(receivedBlockEventsChannelPublisher);
  }

  @Test
  public void shouldBeNotNotifiedOnInvalidBlock() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock validBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    final SignedBeaconBlock invalidBlock =
        validBlock.getSchema().create(validBlock.getMessage(), dataStructureUtil.randomSignature());
    incrementSlot();
    invalidBlockRoots.put(
        invalidBlock.getRoot(), BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);

    assertThatBlockImport(invalidBlock)
        .isCompletedWithValueMatching(result -> !result.isSuccessful());
    verifyNoInteractions(receivedBlockEventsChannelPublisher);
  }

  @Test
  public void shouldPutUnattachedBlockToPending() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final UInt64 nextNextSlot = nextSlot.plus(UInt64.ONE);
    // Create 2 blocks
    localChain.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock nextNextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextNextSlot).getBlock();

    incrementSlot();
    incrementSlot();
    safeJoinBlockImport(nextNextBlock);
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.contains(nextNextBlock)).isTrue();
  }

  @Test
  public void onGossipedBlock_retryIfParentWasUnknownButIsNowAvailable() {
    final BlockImporter blockImporter = mock(BlockImporter.class);
    final RecentChainData localRecentChainData = mock(RecentChainData.class);
    blockManager =
        new BlockManager(
            localRecentChainData,
            blockImporter,
            blockBlobSidecarsTrackersPool,
            pendingBlocks,
            futureBlocks,
            invalidBlockRoots,
            blockValidator,
            timeProvider,
            eventLogger,
            Optional.empty());
    forwardBlockImportedNotificationsTo(blockManager);
    assertThat(blockManager.start()).isCompleted();

    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final UInt64 nextNextSlot = nextSlot.plus(UInt64.ONE);
    // Create 2 blocks
    localChain.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock nextNextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextNextSlot).getBlock();

    final SafeFuture<BlockImportResult> blockImportResult = new SafeFuture<>();
    when(blockImporter.importBlock(eq(nextNextBlock), eq(Optional.empty()), any()))
        .thenReturn(blockImportResult)
        .thenReturn(new SafeFuture<>());

    incrementSlot();
    incrementSlot();
    assertThatBlockImport(nextNextBlock).isNotCompleted();
    ignoreFuture(verify(blockImporter).importBlock(eq(nextNextBlock), eq(Optional.empty()), any()));

    // Before nextNextBlock imports, it's parent becomes available
    when(localRecentChainData.containsBlock(nextNextBlock.getParentRoot())).thenReturn(true);

    // So when the block import completes, it should be retried
    blockImportResult.complete(BlockImportResult.FAILED_UNKNOWN_PARENT);
    ignoreFuture(
        verify(blockImporter, times(2))
            .importBlock(eq(nextNextBlock), eq(Optional.empty()), any()));

    assertThat(pendingBlocks.contains(nextNextBlock)).isFalse();
  }

  @Test
  public void onGossipedBlock_futureBlock() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    safeJoinBlockImport(nextBlock);
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.contains(nextBlock)).isTrue();
  }

  @Test
  public void onGossipedBlock_unattachedFutureBlock() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final UInt64 nextNextSlot = nextSlot.plus(UInt64.ONE);
    // Create 2 blocks
    localChain.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock nextNextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextNextSlot).getBlock();

    incrementSlot();
    safeJoinBlockImport(nextNextBlock);
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.contains(nextNextBlock)).isTrue();
  }

  @Test
  public void onProposedBlock_shouldImport() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    incrementSlot();

    assertThatBlockImport(nextBlock).isCompleted();
    assertThat(pendingBlocks.size()).isEqualTo(0);

    // pool should get notified for new block and then should be notified to drop content due to
    // block import completion
    verify(blockBlobSidecarsTrackersPool).onNewBlock(nextBlock, Optional.empty());
    verify(blockBlobSidecarsTrackersPool).removeAllForBlock(nextBlock.getRoot());
  }

  @Test
  public void onProposedBlock_futureBlock() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    assertThatBlockImport(nextBlock).isCompleted();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.contains(nextBlock)).isTrue();

    // blob pool should be notified about new block only
    verify(blockBlobSidecarsTrackersPool).onNewBlock(nextBlock, Optional.empty());
    verifyNoMoreInteractions(blockBlobSidecarsTrackersPool);
  }

  @Test
  public void onBlockImported_withPendingBlocks() {
    final int blockCount = 3;
    final List<SignedBeaconBlock> blocks = new ArrayList<>(blockCount);

    for (int i = 0; i < blockCount; i++) {
      final UInt64 nextSlot = incrementSlot();
      blocks.add(localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock());
    }

    // Gossip all blocks except the first
    blocks
        .subList(1, blockCount)
        .forEach(block -> blockManager.importBlock(block, BroadcastValidationLevel.NOT_REQUIRED));
    assertThat(pendingBlocks.size()).isEqualTo(blockCount - 1);

    // Import next block, causing remaining blocks to be imported
    assertImportBlockSuccessfully(blocks.get(0));
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImportFailure_withPendingDependantBlocks() {
    final int invalidChainDepth = 3;
    final List<SignedBeaconBlock> invalidBlockDescendants = new ArrayList<>(invalidChainDepth);

    final SignedBeaconBlock invalidBlock =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(incrementSlot(), BlockOptions.create().setWrongProposer(true))
            .getBlock();
    Bytes32 parentBlockRoot = invalidBlock.getMessage().hashTreeRoot();
    for (int i = 0; i < invalidChainDepth; i++) {
      final UInt64 nextSlot = incrementSlot();
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(nextSlot.longValue(), parentBlockRoot);
      invalidBlockDescendants.add(block);
      parentBlockRoot = block.getMessage().hashTreeRoot();
    }

    // Gossip all blocks except the first
    invalidBlockDescendants.forEach(
        invalidBlockDescendant ->
            assertImportBlockWithResult(
                invalidBlockDescendant, BlockImportResult.FAILED_UNKNOWN_PARENT));
    assertThat(pendingBlocks.size()).isEqualTo(invalidChainDepth);

    // Gossip next block, causing dependent blocks to be dropped when the import fails
    assertImportBlockWithResult(invalidBlock, FailureReason.FAILED_STATE_TRANSITION);
    assertThat(pendingBlocks.size()).isEqualTo(0);

    // verify blob sidecars pool get notified to drop content
    invalidBlockDescendants.forEach(
        invalidBlockDescendant ->
            verify(blockBlobSidecarsTrackersPool)
                .removeAllForBlock(invalidBlockDescendant.getRoot()));

    // If any invalid block is again gossiped, it should be ignored
    invalidBlockDescendants.forEach(
        invalidBlockDescendant ->
            assertImportBlockWithResult(
                invalidBlockDescendant, BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK));
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImportFailure_withUnconnectedPendingDependantBlocks() {
    final int invalidChainDepth = 3;
    final List<SignedBeaconBlock> invalidBlockDescendants = new ArrayList<>(invalidChainDepth);

    final SignedBeaconBlock invalidBlock =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(incrementSlot(), BlockOptions.create().setWrongProposer(true))
            .getBlock();
    Bytes32 parentBlockRoot = invalidBlock.getMessage().hashTreeRoot();
    for (int i = 0; i < invalidChainDepth; i++) {
      final UInt64 nextSlot = incrementSlot();
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(nextSlot.longValue(), parentBlockRoot);
      invalidBlockDescendants.add(block);
      parentBlockRoot = block.getMessage().hashTreeRoot();
    }

    // Gossip all blocks except the first two
    invalidBlockDescendants
        .subList(1, invalidChainDepth)
        .forEach(
            invalidBlockDescendant ->
                assertImportBlockWithResult(
                    invalidBlockDescendant, BlockImportResult.FAILED_UNKNOWN_PARENT));
    assertThat(pendingBlocks.size()).isEqualTo(invalidChainDepth - 1);

    // Gossip invalid block, which should fail to import and be marked invalid
    assertImportBlockWithResult(invalidBlock, FailureReason.FAILED_STATE_TRANSITION);
    assertThat(pendingBlocks.size()).isEqualTo(invalidChainDepth - 1);

    // Gossip the child of the invalid block, which should also be marked invalid causing
    // the rest of the chain to be marked invalid and dropped
    assertImportBlockWithResult(
        invalidBlockDescendants.get(0), BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);
    assertThat(pendingBlocks.size()).isEqualTo(0);

    // If the last block is imported, it should be rejected
    assertImportBlockWithResult(
        invalidBlockDescendants.get(invalidChainDepth - 1),
        BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImported_withPendingFutureBlocks() {
    final int blockCount = 3;
    final List<SignedBeaconBlock> blocks = new ArrayList<>(blockCount);

    // Update local slot to match the first new block
    incrementSlot();
    for (int i = 0; i < blockCount; i++) {
      final UInt64 nextSlot = GENESIS_SLOT.plus(i + 1);
      blocks.add(localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock());
    }

    // Gossip all blocks except the first
    blocks
        .subList(1, blockCount)
        .forEach(block -> blockManager.importBlock(block, BroadcastValidationLevel.NOT_REQUIRED));
    assertThat(pendingBlocks.size()).isEqualTo(blockCount - 1);

    // Import next block, causing next block to be queued for import
    final SignedBeaconBlock firstBlock = blocks.get(0);
    assertImportBlockSuccessfully(firstBlock);
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.size()).isEqualTo(1);

    // Increment slot so that we can import the next block
    incrementSlot();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(1);

    // Increment slot so that we can import the next block
    incrementSlot();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImported_withPendingDescendantsOfFailedExecutionPayloadExecutionBlock() {
    final int blockCount = 3;
    final List<SignedBeaconBlock> blocks = new ArrayList<>(blockCount);

    for (int i = 0; i < blockCount; i++) {
      final UInt64 nextSlot = incrementSlot();
      blocks.add(localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock());
    }

    // gossip the first block with execution payload failure
    executionLayer.setPayloadStatus(PayloadStatus.failedExecution(new Error("error")));
    assertImportBlockWithResult(blocks.get(0), FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION);

    // EL is now alive
    executionLayer.setPayloadStatus(PayloadStatus.VALID);

    // Gossip all remaining blocks
    blocks
        .subList(1, blockCount)
        .forEach(
            block -> assertImportBlockWithResult(block, BlockImportResult.FAILED_UNKNOWN_PARENT));
    assertThat(pendingBlocks.size()).isEqualTo(blockCount - 1);

    // Import first block again (from gossip or ReexecutingExecutionPayloadBlockManagerTest)
    // expecting all to be imported
    assertImportBlockSuccessfully(blocks.get(0));
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onValidateAndImportBlock_shouldEarlyRejectInvalidBlocks() {
    final int invalidChainDepth = 3;
    final List<SignedBeaconBlock> invalidBlockDescendants = new ArrayList<>(invalidChainDepth);

    final SignedBeaconBlock invalidBlock =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(incrementSlot(), BlockOptions.create().setWrongProposer(true))
            .getBlock();
    Bytes32 parentBlockRoot = invalidBlock.getMessage().hashTreeRoot();
    for (int i = 0; i < invalidChainDepth; i++) {
      final UInt64 nextSlot = incrementSlot();
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(nextSlot.longValue(), parentBlockRoot);
      invalidBlockDescendants.add(block);
      parentBlockRoot = block.getMessage().hashTreeRoot();
    }

    // import invalid block, which should fail to import and be marked invalid
    assertImportBlockWithResult(invalidBlock, FailureReason.FAILED_STATE_TRANSITION);

    reset(blockBlobSidecarsTrackersPool);

    // Gossip same invalid block, must reject with no actual validation
    assertValidateAndImportBlockRejectWithoutValidation(invalidBlock);

    // Gossip invalid block descendants, must reject with no actual validation
    invalidBlockDescendants.forEach(this::assertValidateAndImportBlockRejectWithoutValidation);

    // If any invalid block is again imported, it should be ignored
    invalidBlockDescendants.forEach(
        invalidBlockDescendant ->
            assertImportBlockWithResult(
                invalidBlockDescendant, BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK));

    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  void onImportBlock_shouldImportWithBroadcastValidationCompletedWhileStillImporting() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    incrementSlot();

    when(blockValidator.initiateBroadcastValidation(eq(nextBlock), eq(GOSSIP)))
        .thenReturn(BlockBroadcastValidator.NOOP);

    // let's delay EL so importResult SafeFuture doesn't complete
    final SafeFuture<PayloadStatus> payloadStatusSafeFuture = new SafeFuture<>();
    doReturn(payloadStatusSafeFuture).when(executionLayer).engineNewPayload(any());

    final Optional<ChainHead> preImportHead = localRecentChainData.getChainHead();

    // gossip validation passes so we expect a success immediate result
    assertThatBlockImportAndBroadcastValidationResults(nextBlock, GOSSIP)
        .isCompletedWithValueMatching(
            blockImportAndBroadcastValidationResults -> {
              assertThatSafeFuture(blockImportAndBroadcastValidationResults.blockImportResult())
                  .isNotCompleted();
              assertThatSafeFuture(
                      blockImportAndBroadcastValidationResults.broadcastValidationResult())
                  .isCompletedWithValue(SUCCESS);
              return true;
            });

    final Optional<ChainHead> postImportHead = localRecentChainData.getChainHead();

    // chain head has not changed so far
    assertThat(preImportHead).isEqualTo(postImportHead);

    // let's EL return VALID
    payloadStatusSafeFuture.complete(PayloadStatus.VALID);

    final Bytes32 importActuallyDoneHeadRoot =
        localRecentChainData.getChainHead().orElseThrow().getRoot();

    // the block has actually imported and the head root corresponds to nextBlock root
    assertThat(importActuallyDoneHeadRoot).isEqualTo(nextBlock.getRoot());
  }

  @Test
  void onImportBlock_shouldNotImportWithBroadcastValidationWhenImportFails() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    incrementSlot();

    final BlockBroadcastValidator blockBroadcastValidator = mock(BlockBroadcastValidator.class);
    when(blockValidator.initiateBroadcastValidation(eq(nextBlock), eq(GOSSIP)))
        .thenReturn(blockBroadcastValidator);

    when(blockBroadcastValidator.getResult())
        .thenReturn(SafeFuture.completedFuture(BroadcastValidationResult.GOSSIP_FAILURE));

    assertThatBlockImportAndBroadcastValidationResults(nextBlock, GOSSIP)
        .isCompletedWithValueMatching(
            blockImportAndBroadcastValidationResults -> {
              assertThatSafeFuture(blockImportAndBroadcastValidationResults.blockImportResult())
                  .isCompletedWithValueMatching(
                      result ->
                          result
                              .getFailureReason()
                              .equals(FailureReason.FAILED_BROADCAST_VALIDATION));
              assertThatSafeFuture(
                      blockImportAndBroadcastValidationResults.broadcastValidationResult())
                  .isCompletedWithValue(BroadcastValidationResult.GOSSIP_FAILURE);
              return true;
            });
    assertThat(invalidBlockRoots).doesNotContainKeys(nextBlock.getRoot());
  }

  @Test
  void onValidateAndImportBlock_shouldLogSlowImport() {
    final SignedBeaconBlock block =
        localChain.chainBuilder().generateBlockAtSlot(incrementSlot()).getBlock();
    // slot 1 - secondPerSlot 6

    // 1 second late
    final Optional<UInt64> arrivalTime = Optional.of(UInt64.valueOf(7000));
    // gossip validation time
    timeProvider.advanceTimeByMillis(7_500);

    when(blockValidator.validateGossip(any()))
        .thenAnswer(
            invocation -> {
              // advance to simulate processing time of 3000ms
              // we are now 4s into the slot (threshold for warning is 2)
              timeProvider.advanceTimeByMillis(3_000);
              return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
            });

    assertThat(blockManager.validateAndImportBlock(block, arrivalTime))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    verify(eventLogger)
        .lateBlockImport(
            block.getRoot(),
            block.getSlot(),
            block.getProposerIndex(),
            ARRIVAL_EVENT_LABEL
                + " 1000ms, "
                + GOSSIP_VALIDATION_EVENT_LABEL
                + " +500ms, "
                + PRESTATE_RETRIEVED_EVENT_LABEL
                + " +3000ms, "
                + PROCESSED_EVENT_LABEL
                + " +0ms, "
                + DATA_AVAILABILITY_CHECKED_LABEL
                + " +0ms, "
                + EXECUTION_PAYLOAD_RESULT_RECEIVED_LABEL
                + " +0ms, "
                + BEGIN_IMPORTING_LABEL
                + " +0ms, "
                + TRANSACTION_PREPARED_EVENT_LABEL
                + " +0ms, "
                + TRANSACTION_COMMITTED_EVENT_LABEL
                + " +0ms, "
                + COMPLETED_EVENT_LABEL
                + " +0ms",
            "success");
  }

  @Test
  void onValidateAndImportBlock_shouldNotLogSlowImport() {
    final SignedBeaconBlock block =
        localChain.chainBuilder().generateBlockAtSlot(incrementSlot()).getBlock();
    // slot 1 - secondPerSlot 6

    // arrival time
    timeProvider.advanceTimeByMillis(7_000); // 1 second late

    when(blockValidator.validateGossip(any()))
        .thenAnswer(
            invocation -> {
              // advance to simulate processing time of 500ms
              // we are now 1.5s into the slot (threshold for warning is 2)
              timeProvider.advanceTimeByMillis(500);
              return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
            });

    assertThat(blockManager.validateAndImportBlock(block, Optional.empty()))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    verifyNoInteractions(eventLogger);
  }

  @Test
  void onDeneb_shouldStoreBlobSidecarsAlongWithBlock() {
    // If we start genesis with Deneb, 0 will be earliestBlobSidecarSlot, so started on epoch 1
    setupWithSpec(TestSpecFactory.createMinimalWithDenebForkEpoch(UInt64.valueOf(1)));
    final UInt64 slotsPerEpoch = UInt64.valueOf(spec.slotsPerEpoch(UInt64.ZERO));
    incrementSlotTo(slotsPerEpoch);

    // Import block 1 with blobSidecars
    final SignedBlockAndState signedBlockAndState1 =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(currentSlot, BlockOptions.create().setGenerateRandomBlobs(true));
    final List<BlobSidecar> blobSidecars1 =
        localChain.chainBuilder().getBlobSidecars(signedBlockAndState1.getRoot());
    assertThatNothingStoredForSlotRoot(signedBlockAndState1.getSlotAndBlockRoot());
    assertThat(blobSidecars1).isNotEmpty();
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);

    final SignedBeaconBlock block1 = signedBlockAndState1.getBlock();
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithValidBlobSidecars(block1, blobSidecars1);

    assertThatBlockImport(block1).isCompletedWithValueMatching(BlockImportResult::isSuccessful);
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThatStored(block1.getMessage(), blobSidecars1);
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValue(Optional.of(signedBlockAndState1.getSlot()));

    // Import block 2 with blobSidecars
    final SignedBlockAndState signedBlockAndState2 =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(
                incrementSlot(), BlockOptions.create().setGenerateRandomBlobs(true));
    final List<BlobSidecar> blobSidecars2 =
        localChain.chainBuilder().getBlobSidecars(signedBlockAndState2.getRoot());
    assertThat(signedBlockAndState2.getSlot()).isEqualTo(signedBlockAndState1.getSlot().plus(1));
    assertThat(blobSidecars2).isNotEmpty();
    assertThatNothingStoredForSlotRoot(signedBlockAndState2.getSlotAndBlockRoot());

    final SignedBeaconBlock block2 = signedBlockAndState2.getBlock();
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker2 =
        createAvailabilityCheckerWithValidBlobSidecars(block2, blobSidecars2);

    assertThatBlockImport(block2).isCompletedWithValueMatching(BlockImportResult::isSuccessful);
    verify(blobSidecarsAvailabilityChecker2).getAvailabilityCheckResult();
    assertThatStored(block2.getMessage(), blobSidecars2);
    // Have not changed
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValue(Optional.of(signedBlockAndState1.getSlot()));

    // Import block 3 with empty blobSidecars
    final SignedBlockAndState signedBlockAndState3 =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(
                incrementSlot(), BlockOptions.create().setBlobSidecars(Collections.emptyList()));
    final List<BlobSidecar> blobSidecars3 =
        localChain.chainBuilder().getBlobSidecars(signedBlockAndState3.getRoot());
    assertThat(signedBlockAndState3.getSlot()).isEqualTo(signedBlockAndState2.getSlot().plus(1));
    assertThat(blobSidecars3).isEmpty();
    assertThatNothingStoredForSlotRoot(signedBlockAndState3.getSlotAndBlockRoot());

    final SignedBeaconBlock block3 = signedBlockAndState3.getBlock();
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker3 =
        createAvailabilityCheckerWithValidBlobSidecars(block3, blobSidecars3);

    assertThatBlockImport(block3).isCompletedWithValueMatching(BlockImportResult::isSuccessful);
    verify(blobSidecarsAvailabilityChecker3).getAvailabilityCheckResult();
    assertThatStored(block3.getMessage(), blobSidecars3);
    // Have not changed
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValue(Optional.of(signedBlockAndState1.getSlot()));
  }

  @Test
  void onDeneb_shouldStoreEarliestBlobSidecarSlotCorrectlyWhenItsDenebGenesis() {
    currentSlot = currentSlot.plus(10);
    localChain.chainUpdater().setCurrentSlot(currentSlot);
    blockManager.onSlot(currentSlot);
    final SignedBlockAndState signedBlockAndState1 =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(currentSlot, BlockOptions.create().setGenerateRandomBlobs(true));
    final List<BlobSidecar> blobSidecars1 =
        localChain.chainBuilder().getBlobSidecars(signedBlockAndState1.getRoot());
    assertThat(blobSidecars1).isNotEmpty();

    final SignedBeaconBlock block1 = signedBlockAndState1.getBlock();
    assertThat(block1.getSlot()).isEqualTo(UInt64.valueOf(10));
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithValidBlobSidecars(block1, blobSidecars1);

    assertThatBlockImport(block1).isCompletedWithValueMatching(BlockImportResult::isSuccessful);
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThatStored(block1.getMessage(), blobSidecars1);
    // Should be 0, if Genesis is Deneb
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValue(Optional.of(UInt64.valueOf(0)));
  }

  @Test
  void onDeneb_shouldStoreEarliestBlobSidecarSlotCorrectlyWhenThereIsGap() {
    setupWithSpec(TestSpecFactory.createMinimalWithDenebForkEpoch(UInt64.valueOf(1)));
    final UInt64 slotsPerEpoch = UInt64.valueOf(spec.slotsPerEpoch(UInt64.ZERO));

    currentSlot = currentSlot.plus(slotsPerEpoch.plus(2));
    localChain.chainUpdater().setCurrentSlot(currentSlot);
    blockManager.onSlot(currentSlot);
    final SignedBlockAndState signedBlockAndState1 =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(currentSlot, BlockOptions.create().setGenerateRandomBlobs(true));
    final List<BlobSidecar> blobSidecars1 =
        localChain.chainBuilder().getBlobSidecars(signedBlockAndState1.getRoot());
    assertThat(blobSidecars1).isNotEmpty();

    final SignedBeaconBlock block1 = signedBlockAndState1.getBlock();
    assertThat(block1.getSlot()).isEqualTo(UInt64.valueOf(10));
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithValidBlobSidecars(block1, blobSidecars1);

    assertThatBlockImport(block1).isCompletedWithValueMatching(BlockImportResult::isSuccessful);
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThatStored(block1.getMessage(), blobSidecars1);
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValue(Optional.of(slotsPerEpoch));
  }

  @Test
  void onDeneb_shouldStoreBlockWhenBlobSidecarsNotRequired() {
    // If we start genesis with Deneb, 0 will be earliestBlobSidecarSlot, so started on epoch 1
    setupWithSpec(TestSpecFactory.createMinimalWithDenebForkEpoch(UInt64.valueOf(1)));
    final UInt64 slotsPerEpoch = UInt64.valueOf(spec.slotsPerEpoch(UInt64.ZERO));
    incrementSlotTo(slotsPerEpoch);

    final SignedBlockAndState signedBlockAndState1 =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(currentSlot, BlockOptions.create().setGenerateRandomBlobs(true));
    final List<BlobSidecar> blobSidecars1 =
        localChain.chainBuilder().getBlobSidecars(signedBlockAndState1.getRoot());
    assertThatNothingStoredForSlotRoot(signedBlockAndState1.getSlotAndBlockRoot());
    assertThat(blobSidecars1).isNotEmpty();
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);

    final SignedBeaconBlock block1 = signedBlockAndState1.getBlock();
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithNotRequiredBlobSidecars(block1);

    assertThatBlockImport(block1).isCompletedWithValueMatching(BlockImportResult::isSuccessful);
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThat(localRecentChainData.retrieveBlockByRoot(block1.getRoot()))
        .isCompletedWithValue(Optional.of(block1.getMessage()));
    assertThat(localRecentChainData.getBlobSidecars(block1.getSlotAndBlockRoot())).isEmpty();
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);
  }

  @Test
  void onDeneb_shouldNotStoreBlockWhenBlobSidecarsIsInvalid() {
    // If we start genesis with Deneb, 0 will be earliestBlobSidecarSlot, so started on epoch 1
    setupWithSpec(TestSpecFactory.createMinimalWithDenebForkEpoch(UInt64.valueOf(1)));
    final UInt64 slotsPerEpoch = UInt64.valueOf(spec.slotsPerEpoch(UInt64.ZERO));
    incrementSlotTo(slotsPerEpoch);

    final SignedBlockAndState signedBlockAndState1 =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(currentSlot, BlockOptions.create().setGenerateRandomBlobs(true));
    final SignedBlockAndState signedBlockAndState2 =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(
                currentSlot.increment(), BlockOptions.create().setGenerateRandomBlobs(true));

    final List<BlobSidecar> blobSidecars1 =
        localChain.chainBuilder().getBlobSidecars(signedBlockAndState1.getRoot());
    assertThatNothingStoredForSlotRoot(signedBlockAndState1.getSlotAndBlockRoot());
    assertThat(blobSidecars1).isNotEmpty();
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);

    final SignedBeaconBlock block1 = signedBlockAndState1.getBlock();
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithInvalidBlobSidecars(block1, blobSidecars1);

    assertThatBlockImport(block1)
        .isCompletedWithValueMatching(
            cause -> cause.getFailureReason().equals(FAILED_DATA_AVAILABILITY_CHECK_INVALID));
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThat(localRecentChainData.retrieveBlockByRoot(block1.getRoot()))
        .isCompletedWithValue(Optional.empty());
    assertThat(localRecentChainData.getBlobSidecars(block1.getSlotAndBlockRoot())).isEmpty();
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);

    verify(blockBlobSidecarsTrackersPool).removeAllForBlock(block1.getRoot());
    assertThat(invalidBlockRoots).doesNotContainKeys(block1.getRoot());

    // if we receive a block building on top of block1, we should trigger unknown parent flow
    assertThatBlockImport(signedBlockAndState2.getBlock())
        .isCompletedWithValueMatching(cause -> cause.getFailureReason().equals(UNKNOWN_PARENT));
  }

  @Test
  void onDeneb_shouldNotStoreBlockWhenBlobSidecarsIsNotAvailable() {
    // If we start genesis with Deneb, 0 will be earliestBlobSidecarSlot, so started on epoch 1
    setupWithSpec(TestSpecFactory.createMinimalWithDenebForkEpoch(UInt64.valueOf(1)));
    final UInt64 slotsPerEpoch = UInt64.valueOf(spec.slotsPerEpoch(UInt64.ZERO));
    incrementSlotTo(slotsPerEpoch);

    final SignedBlockAndState signedBlockAndState1 =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(currentSlot, BlockOptions.create().setGenerateRandomBlobs(true));
    final List<BlobSidecar> blobSidecars1 =
        localChain.chainBuilder().getBlobSidecars(signedBlockAndState1.getRoot());
    assertThatNothingStoredForSlotRoot(signedBlockAndState1.getSlotAndBlockRoot());
    assertThat(blobSidecars1).isNotEmpty();
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);

    final SignedBeaconBlock block1 = signedBlockAndState1.getBlock();
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithNotAvailableBlobSidecars(block1);

    assertThatBlockImport(block1)
        .isCompletedWithValueMatching(
            cause -> cause.getFailureReason().equals(FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE));
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThat(localRecentChainData.retrieveBlockByRoot(block1.getRoot()))
        .isCompletedWithValue(Optional.empty());
    assertThat(localRecentChainData.getBlobSidecars(block1.getSlotAndBlockRoot())).isEmpty();
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);
    assertThat(invalidBlockRoots).doesNotContainKeys(block1.getRoot());
  }

  @Test
  void preDeneb_shouldNotWorryAboutBlobSidecars() {
    setupWithSpec(TestSpecFactory.createMinimalCapella());
    final SignedBlockAndState signedBlockAndState1 =
        localChain
            .chainBuilder()
            .generateBlockAtSlot(
                incrementSlot(), BlockOptions.create().setGenerateRandomBlobs(true));
    final List<BlobSidecar> blobSidecars1 =
        localChain.chainBuilder().getBlobSidecars(signedBlockAndState1.getRoot());
    assertThatNothingStoredForSlotRoot(signedBlockAndState1.getSlotAndBlockRoot());
    assertThat(blobSidecars1).isEmpty();
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);

    final SignedBeaconBlock block1 = signedBlockAndState1.getBlock();
    // pre-Deneb is used NOOP with default not required
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithNotRequiredBlobSidecars(block1);

    assertThatBlockImport(block1).isCompletedWithValueMatching(BlockImportResult::isSuccessful);
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThat(localRecentChainData.retrieveBlockByRoot(block1.getRoot()))
        .isCompletedWithValue(Optional.of(block1.getMessage()));
    assertThat(localRecentChainData.getBlobSidecars(block1.getSlotAndBlockRoot())).isEmpty();
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);
  }

  private AvailabilityChecker<BlobSidecar> createAvailabilityCheckerWithValidBlobSidecars(
      final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {
    reset(blobSidecarManager);
    @SuppressWarnings("unchecked")
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker =
        mock(AvailabilityChecker.class);
    when(blobSidecarManager.createAvailabilityChecker(eq(block)))
        .thenReturn(blobSidecarsAvailabilityChecker);
    when(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(SafeFuture.completedFuture(DataAndValidationResult.validResult(blobSidecars)));
    return blobSidecarsAvailabilityChecker;
  }

  private AvailabilityChecker<BlobSidecar> createAvailabilityCheckerWithNotRequiredBlobSidecars(
      final SignedBeaconBlock block) {
    reset(blobSidecarManager);
    @SuppressWarnings("unchecked")
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker =
        mock(AvailabilityChecker.class);
    when(blobSidecarManager.createAvailabilityChecker(eq(block)))
        .thenReturn(blobSidecarsAvailabilityChecker);
    when(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(DataAndValidationResult.notRequiredResultFuture());
    return blobSidecarsAvailabilityChecker;
  }

  private AvailabilityChecker<BlobSidecar> createAvailabilityCheckerWithNotAvailableBlobSidecars(
      final SignedBeaconBlock block) {
    reset(blobSidecarManager);
    @SuppressWarnings("unchecked")
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker =
        mock(AvailabilityChecker.class);
    when(blobSidecarManager.createAvailabilityChecker(eq(block)))
        .thenReturn(blobSidecarsAvailabilityChecker);
    when(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(SafeFuture.completedFuture(DataAndValidationResult.notAvailable()));
    return blobSidecarsAvailabilityChecker;
  }

  private AvailabilityChecker<BlobSidecar> createAvailabilityCheckerWithInvalidBlobSidecars(
      final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {
    reset(blobSidecarManager);
    @SuppressWarnings("unchecked")
    final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker =
        mock(AvailabilityChecker.class);
    when(blobSidecarManager.createAvailabilityChecker(eq(block)))
        .thenReturn(blobSidecarsAvailabilityChecker);
    when(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(
            SafeFuture.completedFuture(
                DataAndValidationResult.invalidResult(blobSidecars, new RuntimeException("ouch"))));
    return blobSidecarsAvailabilityChecker;
  }

  private void assertThatStored(
      final BeaconBlock beaconBlock, final List<BlobSidecar> blobSidecars) {
    assertThat(localRecentChainData.retrieveBlockByRoot(beaconBlock.getRoot()))
        .isCompletedWithValue(Optional.of(beaconBlock));
    assertThat(localRecentChainData.getBlobSidecars(beaconBlock.getSlotAndBlockRoot()))
        .contains(blobSidecars);
  }

  private void assertThatNothingStoredForSlotRoot(final SlotAndBlockRoot slotAndBlockRoot) {
    assertThat(localRecentChainData.retrieveBlockByRoot(slotAndBlockRoot.getBlockRoot()))
        .isCompletedWithValueMatching(Optional::isEmpty);
    assertThat(localRecentChainData.getBlobSidecars(slotAndBlockRoot)).isEmpty();
  }

  private void assertImportBlockWithResult(
      final SignedBeaconBlock block, final FailureReason failureReason) {
    assertThatBlockImport(block)
        .isCompletedWithValueMatching(result -> result.getFailureReason().equals(failureReason));
  }

  private void assertImportBlockWithResult(
      final SignedBeaconBlock block, final BlockImportResult importResult) {
    assertThatBlockImport(block)
        .isCompletedWithValueMatching(result -> result.equals(importResult));
  }

  private void assertValidateAndImportBlockRejectWithoutValidation(final SignedBeaconBlock block) {
    assertThat(blockManager.validateAndImportBlock(block, Optional.empty()))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
    verify(blockValidator, never()).validateGossip(eq(block));
    verify(blockBlobSidecarsTrackersPool).removeAllForBlock(block.getRoot());
  }

  private void assertImportBlockSuccessfully(final SignedBeaconBlock block) {
    assertThatBlockImport(block).isCompletedWithValueMatching(BlockImportResult::isSuccessful);
  }

  private void incrementSlotTo(final UInt64 toSlotInclusive) {
    assertThat(toSlotInclusive.isGreaterThanOrEqualTo(currentSlot)).isTrue();
    while (toSlotInclusive.isGreaterThan(currentSlot)) {
      incrementSlot();
    }
  }

  private UInt64 incrementSlot() {
    currentSlot = currentSlot.plus(UInt64.ONE);
    localChain.chainUpdater().setCurrentSlot(currentSlot);
    blockManager.onSlot(currentSlot);
    return currentSlot;
  }

  private BlockManager setupBlockManagerWithMockRecentChainData(
      final RecentChainData localRecentChainData, final boolean isChainHeadOptimistic) {
    when(localRecentChainData.isChainHeadOptimistic()).thenReturn(isChainHeadOptimistic);
    return new BlockManager(
        localRecentChainData,
        blockImporter,
        blockBlobSidecarsTrackersPool,
        pendingBlocks,
        futureBlocks,
        invalidBlockRoots,
        blockValidator,
        timeProvider,
        eventLogger,
        Optional.empty());
  }

  private SafeFutureAssert<BlockImportResult> assertThatBlockImport(final SignedBeaconBlock block) {
    return assertThatSafeFuture(
        blockManager
            .importBlock(block, BroadcastValidationLevel.NOT_REQUIRED)
            .thenCompose(BlockImportAndBroadcastValidationResults::blockImportResult));
  }

  private SafeFutureAssert<BlockImportAndBroadcastValidationResults>
      assertThatBlockImportAndBroadcastValidationResults(
          final SignedBeaconBlock block, final BroadcastValidationLevel broadcastValidationLevel) {
    return assertThatSafeFuture(blockManager.importBlock(block, broadcastValidationLevel));
  }

  private void safeJoinBlockImport(final SignedBeaconBlock block) {
    safeJoin(
        blockManager
            .importBlock(block, BroadcastValidationLevel.NOT_REQUIRED)
            .thenCompose(BlockImportAndBroadcastValidationResults::blockImportResult));
  }
}
