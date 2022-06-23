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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.block.ReexecutingExecutionPayloadBlockManager.RETRY_SLOTS;

import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PendingPoolFactory;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class ReexecutingExecutionPayloadBlockManagerTest {
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final EventLogger eventLogger = mock(EventLogger.class);
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BlockImportNotifications blockImportNotifications =
      mock(BlockImportNotifications.class);
  private final UInt64 historicalBlockTolerance = UInt64.valueOf(5);
  private final UInt64 futureBlockTolerance = UInt64.valueOf(2);
  private final int maxPendingBlocks = 10;
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final PendingPool<SignedBeaconBlock> pendingBlocks =
      new PendingPoolFactory(metricsSystem)
          .createForBlocks(spec, historicalBlockTolerance, futureBlockTolerance, maxPendingBlocks);
  private final FutureItems<SignedBeaconBlock> futureBlocks =
      FutureItems.create(SignedBeaconBlock::getSlot, mock(SettableLabelledGauge.class), "blocks");

  private final BlockImporter blockImporter = mock(BlockImporter.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StorageSystem remoteStorageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);

  private void forwardBlockImportedNotificationsTo(final BlockManager blockManager) {
    doAnswer(
            invocation -> {
              blockManager.onBlockImported(invocation.getArgument(0));
              return null;
            })
        .when(blockImportNotifications)
        .onBlockImported(any());
  }

  private final BlockManager blockManager =
      new ReexecutingExecutionPayloadBlockManager(
          recentChainData,
          blockImporter,
          pendingBlocks,
          futureBlocks,
          mock(BlockValidator.class),
          timeProvider,
          eventLogger,
          asyncRunner,
          Optional.empty());

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.blsVerifyDeposit = false;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.blsVerifyDeposit = true;
  }

  @BeforeEach
  public void setup() {
    forwardBlockImportedNotificationsTo(blockManager);
    remoteStorageSystem.chainUpdater().initializeGenesisWithPayload(false);
    assertThat(blockManager.start()).isCompleted();
  }

  @AfterEach
  public void cleanup() {
    assertThat(blockManager.stop()).isCompleted();
  }

  @Test
  public void onFailedExecutionPayloadExecution_shouldRetry() {
    final SignedBeaconBlock nextBlock =
        remoteStorageSystem.chainUpdater().chainBuilder.generateNextBlock().getBlock();

    // syncing
    when(blockImporter.importBlock(nextBlock, Optional.empty()))
        .thenReturn(
            SafeFuture.completedFuture(
                BlockImportResult.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING));

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();

    // communication error
    when(blockImporter.importBlock(nextBlock, Optional.empty()))
        .thenReturn(
            SafeFuture.completedFuture(
                BlockImportResult.failedExecutionPayloadExecution(new RuntimeException("error"))));

    asyncRunner.executeQueuedActions();

    // successful imported now
    when(blockImporter.importBlock(nextBlock, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(BlockImportResult.successful(nextBlock)));

    asyncRunner.executeQueuedActions();

    verify(blockImporter, times(3)).importBlock(nextBlock, Optional.empty());

    // should be dequeued now, so no more interactions
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  public void onFailedExecutionPayloadExecution_shouldRetryForAtLeast2Slots() {
    final SignedBeaconBlock nextBlock =
        remoteStorageSystem.chainUpdater().chainBuilder.generateNextBlock().getBlock();

    // communication error
    when(blockImporter.importBlock(nextBlock, Optional.empty()))
        .thenReturn(
            SafeFuture.completedFuture(
                BlockImportResult.failedExecutionPayloadExecution(new RuntimeException("error"))));

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();
    asyncRunner.executeQueuedActions();

    // Pass 2 slots
    blockManager.onSlot(nextBlock.getSlot().plus(1));
    blockManager.onSlot(nextBlock.getSlot().plus(2));
    asyncRunner.executeQueuedActions();
    verify(blockImporter, times(3)).importBlock(nextBlock, Optional.empty());

    // successful imported now
    when(blockImporter.importBlock(nextBlock, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(BlockImportResult.successful(nextBlock)));

    asyncRunner.executeQueuedActions();
    verify(blockImporter, times(4)).importBlock(nextBlock, Optional.empty());

    // should be dequeued now, so no more interactions
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  public void onInvalidBlockImport_shouldNotRetry() {
    final SignedBeaconBlock nextBlock =
        remoteStorageSystem.chainUpdater().chainBuilder.generateNextBlock().getBlock();

    // invalid
    when(blockImporter.importBlock(nextBlock, Optional.empty()))
        .thenReturn(
            SafeFuture.completedFuture(
                BlockImportResult.failedStateTransition(new IllegalStateException("invalid"))));

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();

    verify(blockImporter, times(1)).importBlock(nextBlock, Optional.empty());

    // should not bw queued
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  public void onSlot_shouldStopRetrying() {
    final SignedBeaconBlock nextBlock =
        remoteStorageSystem.chainUpdater().chainBuilder.generateNextBlock().getBlock();

    // syncing
    when(blockImporter.importBlock(nextBlock, Optional.empty()))
        .thenReturn(
            SafeFuture.completedFuture(
                BlockImportResult.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING));

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();

    verify(blockImporter, times(1)).importBlock(nextBlock, Optional.empty());

    blockManager.onSlot(nextBlock.getSlot().plus(RETRY_SLOTS + 1));

    // should be dequeued now, so no more interactions
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  public void onDescendantPendingChainAdvance_shouldRetryParentExecutionAgain() {
    final SignedBeaconBlock nextBlock =
        remoteStorageSystem.chainUpdater().chainBuilder.generateNextBlock().getBlock();

    // beginning to build descendant chain
    final SignedBeaconBlock descendantBlock =
        remoteStorageSystem.chainUpdater().chainBuilder.generateNextBlock().getBlock();
    pendingBlocks.add(descendantBlock);

    // syncing
    when(blockImporter.importBlock(nextBlock, Optional.empty()))
        .thenReturn(
            SafeFuture.completedFuture(
                BlockImportResult.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING));

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();
    verify(blockImporter, times(1)).importBlock(nextBlock, Optional.empty());

    blockManager.onSlot(nextBlock.getSlot().plus(RETRY_SLOTS + 1));
    // no need for re-execution, retry slots are over
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(blockImporter);

    SignedBeaconBlock anotherChainBlock =
        dataStructureUtil.randomSignedBeaconBlock(nextBlock.getSlot());
    pendingBlocks.add(anotherChainBlock);
    // no need for re-execution, retry slots are over, pending block is not descendant
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(blockImporter);

    final SignedBeaconBlock descendantBlock2 =
        remoteStorageSystem.chainUpdater().chainBuilder.generateNextBlock().getBlock();
    pendingBlocks.add(descendantBlock2);
    // should be re-executed, descendant of chain is added
    asyncRunner.executeQueuedActions();
    verify(blockImporter, times(2)).importBlock(nextBlock, Optional.empty());
  }
}
