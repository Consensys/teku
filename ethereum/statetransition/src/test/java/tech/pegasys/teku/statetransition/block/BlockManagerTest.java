/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason.FAILED_DATA_AVAILABILITY_CHECK_INVALID;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason.FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.ARRIVAL_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.BEGIN_IMPORTING_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.COMPLETED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.EXECUTION_PAYLOAD_RESULT_RECEIVED_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.PRESTATE_RETRIEVED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.PROCESSED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.TRANSACTION_COMMITTED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.TRANSACTION_PREPARED_EVENT_LABEL;

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
import tech.pegasys.teku.spec.datastructures.blocks.ImportedBlockListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarPool;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.StubForkChoiceNotifier;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
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
  private final BlockImportNotifications blockImportNotifications =
      mock(BlockImportNotifications.class);
  private final UInt64 historicalBlockTolerance = UInt64.valueOf(5);
  private final UInt64 futureBlockTolerance = UInt64.valueOf(2);
  private final int maxPendingBlocks = 10;
  private final BlobSidecarPool blobSidecarPool = mock(BlobSidecarPool.class);
  private PendingPool<SignedBeaconBlock> pendingBlocks;
  private final FutureItems<SignedBeaconBlock> futureBlocks =
      FutureItems.create(SignedBeaconBlock::getSlot, mock(SettableLabelledGauge.class), "blocks");
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots =
      LimitedMap.createSynchronized(500);

  private StorageSystem localChain;
  private RecentChainData localRecentChainData;

  private final ForkChoiceNotifier forkChoiceNotifier = new StubForkChoiceNotifier();
  private MergeTransitionBlockValidator transitionBlockValidator;
  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);
  private ForkChoice forkChoice;

  private ExecutionLayerChannelStub executionLayer;
  private final BlockValidator blockValidator = mock(BlockValidator.class);

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
    this.executionLayer = new ExecutionLayerChannelStub(spec, false, Optional.empty());
    this.blockImporter =
        new BlockImporter(
            spec,
            blockImportNotifications,
            localRecentChainData,
            forkChoice,
            WeakSubjectivityFactory.lenientValidator(),
            executionLayer);
    this.blockManager =
        new BlockManager(
            localRecentChainData,
            blockImporter,
            blobSidecarPool,
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
        .thenReturn(BlobSidecarsAvailabilityChecker.NOOP);
  }

  private void forwardBlockImportedNotificationsTo(final BlockManager blockManager) {
    doAnswer(
            invocation -> {
              blockManager.onBlockImported(invocation.getArgument(0));
              return null;
            })
        .when(blockImportNotifications)
        .onBlockImported(any());
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

    safeJoin(blockManager.importBlock(nextBlock));
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void shouldNotifySubscribersOnImport() {
    final ImportedBlockListener subscriber = mock(ImportedBlockListener.class);
    blockManager.subscribeToReceivedBlocks(subscriber);
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    incrementSlot();

    safeJoin(blockManager.importBlock(nextBlock));
    verify(subscriber).onBlockImported(nextBlock, false);
    verify(blobSidecarPool).removeAllForBlock(nextBlock.getRoot());
  }

  @Test
  public void shouldNotifySubscribersOnKnownBlock() {
    final ImportedBlockListener subscriber = mock(ImportedBlockListener.class);
    blockManager.subscribeToReceivedBlocks(subscriber);
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    incrementSlot();

    safeJoin(blockManager.importBlock(nextBlock));
    verify(subscriber).onBlockImported(nextBlock, false);

    assertThatSafeFuture(blockManager.importBlock(nextBlock))
        .isCompletedWithValue(BlockImportResult.knownBlock(nextBlock, false));
    verify(subscriber, times(2)).onBlockImported(nextBlock, false);
  }

  @Test
  public void shouldNotifySubscribersOnKnownOptimisticBlock() {
    final ImportedBlockListener subscriber = mock(ImportedBlockListener.class);
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);
    blockManager.subscribeToReceivedBlocks(subscriber);
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    incrementSlot();

    safeJoin(blockManager.importBlock(nextBlock));
    verify(subscriber).onBlockImported(nextBlock, true);

    assertThatSafeFuture(blockManager.importBlock(nextBlock))
        .isCompletedWithValue(BlockImportResult.knownBlock(nextBlock, true));
    verify(subscriber, times(2)).onBlockImported(nextBlock, true);
  }

  @Test
  public void shouldNotNotifySubscribersOnInvalidBlock() {
    final ImportedBlockListener subscriber = mock(ImportedBlockListener.class);
    blockManager.subscribeToReceivedBlocks(subscriber);
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock validBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    final SignedBeaconBlock invalidBlock =
        validBlock.getSchema().create(validBlock.getMessage(), dataStructureUtil.randomSignature());
    incrementSlot();

    assertThatSafeFuture(blockManager.importBlock(invalidBlock))
        .isCompletedWithValueMatching(result -> !result.isSuccessful());
    verifyNoInteractions(subscriber);
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
    safeJoin(blockManager.importBlock(nextNextBlock));
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.contains(nextNextBlock)).isTrue();
  }

  @Test
  public void onGossipedBlock_retryIfParentWasUnknownButIsNowAvailable() {
    final BlockImporter blockImporter = mock(BlockImporter.class);
    final RecentChainData localRecentChainData = mock(RecentChainData.class);
    final BlockManager blockManager =
        new BlockManager(
            localRecentChainData,
            blockImporter,
            blobSidecarPool,
            pendingBlocks,
            futureBlocks,
            invalidBlockRoots,
            mock(BlockValidator.class),
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
    when(blockImporter.importBlock(nextNextBlock, Optional.empty()))
        .thenReturn(blockImportResult)
        .thenReturn(new SafeFuture<>());

    incrementSlot();
    incrementSlot();
    blockManager.importBlock(nextNextBlock);
    ignoreFuture(verify(blockImporter).importBlock(nextNextBlock, Optional.empty()));

    // Before nextNextBlock imports, it's parent becomes available
    when(localRecentChainData.containsBlock(nextNextBlock.getParentRoot())).thenReturn(true);

    // So when the block import completes, it should be retried
    blockImportResult.complete(BlockImportResult.FAILED_UNKNOWN_PARENT);
    ignoreFuture(verify(blockImporter, times(2)).importBlock(nextNextBlock, Optional.empty()));

    assertThat(pendingBlocks.contains(nextNextBlock)).isFalse();
  }

  @Test
  public void onGossipedBlock_futureBlock() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    safeJoin(blockManager.importBlock(nextBlock));
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
    safeJoin(blockManager.importBlock(nextNextBlock));
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

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();
    assertThat(pendingBlocks.size()).isEqualTo(0);

    // pool should get notified for new block and then should be notified to drop content due to
    // block import completion
    verify(blobSidecarPool).onNewBlock(nextBlock);
    verify(blobSidecarPool).removeAllForBlock(nextBlock.getRoot());
  }

  @Test
  public void onProposedBlock_futureBlock() {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock =
        localChain.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.contains(nextBlock)).isTrue();

    // blob pool should be notified about new block only
    verify(blobSidecarPool).onNewBlock(nextBlock);
    verifyNoMoreInteractions(blobSidecarPool);
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
    blocks.subList(1, blockCount).stream().forEach(blockManager::importBlock);
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
    invalidBlockDescendants.stream()
        .forEach(
            invalidBlockDescendant ->
                assertImportBlockWithResult(
                    invalidBlockDescendant, BlockImportResult.FAILED_UNKNOWN_PARENT));
    assertThat(pendingBlocks.size()).isEqualTo(invalidChainDepth);

    // Gossip next block, causing dependent blocks to be dropped when the import fails
    assertImportBlockWithResult(invalidBlock, FailureReason.FAILED_STATE_TRANSITION);
    assertThat(pendingBlocks.size()).isEqualTo(0);

    // verify blob sidecars pool get notified to drop content
    invalidBlockDescendants.stream()
        .forEach(
            invalidBlockDescendant ->
                verify(blobSidecarPool).removeAllForBlock(invalidBlockDescendant.getRoot()));

    // If any invalid block is again gossiped, it should be ignored
    invalidBlockDescendants.stream()
        .forEach(
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
    invalidBlockDescendants.subList(1, invalidChainDepth).stream()
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
    blocks.subList(1, blockCount).stream().forEach(blockManager::importBlock);
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
    blocks.subList(1, blockCount).stream()
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

    // Gossip same invalid block, must reject with no actual validation
    assertValidateAndImportBlockRejectWithoutValidation(invalidBlock);

    // Gossip invalid block descendants, must reject with no actual validation
    invalidBlockDescendants.stream()
        .forEach(this::assertValidateAndImportBlockRejectWithoutValidation);

    // If any invalid block is again imported, it should be ignored
    invalidBlockDescendants.stream()
        .forEach(
            invalidBlockDescendant ->
                assertImportBlockWithResult(
                    invalidBlockDescendant, BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK));

    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  void onValidateAndImportBlock_shouldLogSlowImport() {
    final SignedBeaconBlock block =
        localChain.chainBuilder().generateBlockAtSlot(incrementSlot()).getBlock();
    // slot 1 - secondPerSlot 6

    // arrival time
    timeProvider.advanceTimeByMillis(7_000); // 1 second late

    when(blockValidator.validate(any()))
        .thenAnswer(
            invocation -> {
              // advance to simulate processing time of 3000ms
              // we are now 4s into the slot (threshold for warning is 2)
              timeProvider.advanceTimeByMillis(3_000);
              return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
            });

    assertThat(blockManager.validateAndImportBlock(block))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    verify(eventLogger)
        .lateBlockImport(
            block.getRoot(),
            block.getSlot(),
            block.getProposerIndex(),
            ARRIVAL_EVENT_LABEL
                + " 1000ms, "
                + PRESTATE_RETRIEVED_EVENT_LABEL
                + " +3000ms, "
                + PROCESSED_EVENT_LABEL
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
                + " +0ms");
  }

  @Test
  void onValidateAndImportBlock_shouldNotLogSlowImport() {
    final SignedBeaconBlock block =
        localChain.chainBuilder().generateBlockAtSlot(incrementSlot()).getBlock();
    // slot 1 - secondPerSlot 6

    // arrival time
    timeProvider.advanceTimeByMillis(7_000); // 1 second late

    when(blockValidator.validate(any()))
        .thenAnswer(
            invocation -> {
              // advance to simulate processing time of 500ms
              // we are now 1.5s into the slot (threshold for warning is 2)
              timeProvider.advanceTimeByMillis(500);
              return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
            });

    assertThat(blockManager.validateAndImportBlock(block))
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
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithValidBlobSidecars(block1, blobSidecars1);

    assertThat(blockManager.importBlock(block1))
        .isCompletedWithValueMatching(BlockImportResult::isSuccessful);
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
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker2 =
        createAvailabilityCheckerWithValidBlobSidecars(block2, blobSidecars2);

    assertThat(blockManager.importBlock(block2))
        .isCompletedWithValueMatching(BlockImportResult::isSuccessful);
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
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker3 =
        createAvailabilityCheckerWithValidBlobSidecars(block3, blobSidecars3);

    assertThat(blockManager.importBlock(block3))
        .isCompletedWithValueMatching(BlockImportResult::isSuccessful);
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
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithValidBlobSidecars(block1, blobSidecars1);

    assertThat(blockManager.importBlock(block1))
        .isCompletedWithValueMatching(BlockImportResult::isSuccessful);
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
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithValidBlobSidecars(block1, blobSidecars1);

    assertThat(blockManager.importBlock(block1))
        .isCompletedWithValueMatching(BlockImportResult::isSuccessful);
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
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithNotRequiredBlobSidecars(block1);

    assertThat(blockManager.importBlock(block1))
        .isCompletedWithValueMatching(BlockImportResult::isSuccessful);
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThat(localRecentChainData.retrieveBlockByRoot(block1.getRoot()))
        .isCompletedWithValue(Optional.of(block1.getMessage()));
    assertThat(localRecentChainData.retrieveBlobSidecars(block1.getSlotAndBlockRoot()))
        .isCompletedWithValue(Collections.emptyList());
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
    final List<BlobSidecar> blobSidecars1 =
        localChain.chainBuilder().getBlobSidecars(signedBlockAndState1.getRoot());
    assertThatNothingStoredForSlotRoot(signedBlockAndState1.getSlotAndBlockRoot());
    assertThat(blobSidecars1).isNotEmpty();
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);

    final SignedBeaconBlock block1 = signedBlockAndState1.getBlock();
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithInvalidBlobSidecars(block1, blobSidecars1);

    assertThat(blockManager.importBlock(block1))
        .isCompletedWithValueMatching(
            cause -> cause.getFailureReason().equals(FAILED_DATA_AVAILABILITY_CHECK_INVALID));
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThat(localRecentChainData.retrieveBlockByRoot(block1.getRoot()))
        .isCompletedWithValue(Optional.empty());
    assertThat(localRecentChainData.retrieveBlobSidecars(block1.getSlotAndBlockRoot()))
        .isCompletedWithValue(Collections.emptyList());
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);
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
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithNotAvailableBlobSidecars(block1);

    assertThat(blockManager.importBlock(block1))
        .isCompletedWithValueMatching(
            cause -> cause.getFailureReason().equals(FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE));
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThat(localRecentChainData.retrieveBlockByRoot(block1.getRoot()))
        .isCompletedWithValue(Optional.empty());
    assertThat(localRecentChainData.retrieveBlobSidecars(block1.getSlotAndBlockRoot()))
        .isCompletedWithValue(Collections.emptyList());
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);
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
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker1 =
        createAvailabilityCheckerWithNotRequiredBlobSidecars(block1);

    assertThat(blockManager.importBlock(block1))
        .isCompletedWithValueMatching(BlockImportResult::isSuccessful);
    verify(blobSidecarsAvailabilityChecker1).getAvailabilityCheckResult();
    assertThat(localRecentChainData.retrieveBlockByRoot(block1.getRoot()))
        .isCompletedWithValue(Optional.of(block1.getMessage()));
    assertThat(localRecentChainData.retrieveBlobSidecars(block1.getSlotAndBlockRoot()))
        .isCompletedWithValue(Collections.emptyList());
    assertThat(localRecentChainData.retrieveEarliestBlobSidecarSlot())
        .isCompletedWithValueMatching(Optional::isEmpty);
  }

  private BlobSidecarsAvailabilityChecker createAvailabilityCheckerWithValidBlobSidecars(
      final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {
    reset(blobSidecarManager);
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker =
        mock(BlobSidecarsAvailabilityChecker.class);
    when(blobSidecarManager.createAvailabilityChecker(eq(block)))
        .thenReturn(blobSidecarsAvailabilityChecker);
    when(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(
            SafeFuture.completedFuture(BlobSidecarsAndValidationResult.validResult(blobSidecars)));
    return blobSidecarsAvailabilityChecker;
  }

  private BlobSidecarsAvailabilityChecker createAvailabilityCheckerWithNotRequiredBlobSidecars(
      final SignedBeaconBlock block) {
    reset(blobSidecarManager);
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker =
        mock(BlobSidecarsAvailabilityChecker.class);
    when(blobSidecarManager.createAvailabilityChecker(eq(block)))
        .thenReturn(blobSidecarsAvailabilityChecker);
    when(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(BlobSidecarsAndValidationResult.NOT_REQUIRED_RESULT_FUTURE);
    return blobSidecarsAvailabilityChecker;
  }

  private BlobSidecarsAvailabilityChecker createAvailabilityCheckerWithNotAvailableBlobSidecars(
      final SignedBeaconBlock block) {
    reset(blobSidecarManager);
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker =
        mock(BlobSidecarsAvailabilityChecker.class);
    when(blobSidecarManager.createAvailabilityChecker(eq(block)))
        .thenReturn(blobSidecarsAvailabilityChecker);
    when(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(SafeFuture.completedFuture(BlobSidecarsAndValidationResult.NOT_AVAILABLE));
    return blobSidecarsAvailabilityChecker;
  }

  private BlobSidecarsAvailabilityChecker createAvailabilityCheckerWithInvalidBlobSidecars(
      final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {
    reset(blobSidecarManager);
    final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker =
        mock(BlobSidecarsAvailabilityChecker.class);
    when(blobSidecarManager.createAvailabilityChecker(eq(block)))
        .thenReturn(blobSidecarsAvailabilityChecker);
    when(blobSidecarsAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(
            SafeFuture.completedFuture(
                BlobSidecarsAndValidationResult.invalidResult(
                    blobSidecars, new RuntimeException("ouch"))));
    return blobSidecarsAvailabilityChecker;
  }

  private void assertThatStored(
      final BeaconBlock beaconBlock, final List<BlobSidecar> blobSidecars) {
    assertThat(localRecentChainData.retrieveBlockByRoot(beaconBlock.getRoot()))
        .isCompletedWithValue(Optional.of(beaconBlock));
    assertThat(localRecentChainData.retrieveBlobSidecars(beaconBlock.getSlotAndBlockRoot()))
        .isCompletedWithValue(blobSidecars);
  }

  private void assertThatNothingStoredForSlotRoot(final SlotAndBlockRoot slotAndBlockRoot) {
    assertThat(localRecentChainData.retrieveBlockByRoot(slotAndBlockRoot.getBlockRoot()))
        .isCompletedWithValueMatching(Optional::isEmpty);
    assertThat(localRecentChainData.retrieveBlobSidecars(slotAndBlockRoot))
        .isCompletedWithValueMatching(List::isEmpty);
  }

  private void assertImportBlockWithResult(SignedBeaconBlock block, FailureReason failureReason) {
    assertThat(blockManager.importBlock(block))
        .isCompletedWithValueMatching(result -> result.getFailureReason().equals(failureReason));
  }

  private void assertImportBlockWithResult(
      SignedBeaconBlock block, BlockImportResult importResult) {
    assertThat(blockManager.importBlock(block))
        .isCompletedWithValueMatching(result -> result.equals(importResult));
  }

  private void assertValidateAndImportBlockRejectWithoutValidation(final SignedBeaconBlock block) {
    assertThat(blockManager.validateAndImportBlock(block))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
    verify(blockValidator, never()).validate(block);
  }

  private void assertImportBlockSuccessfully(SignedBeaconBlock block) {
    assertThat(blockManager.importBlock(block))
        .isCompletedWithValueMatching(BlockImportResult::isSuccessful);
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
}
