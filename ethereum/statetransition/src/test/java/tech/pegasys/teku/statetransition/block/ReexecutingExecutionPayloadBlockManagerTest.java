/*
 * Copyright 2022 ConsenSys AG.
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
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class ReexecutingExecutionPayloadBlockManagerTest {
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final BlockImportNotifications blockImportNotifications =
      mock(BlockImportNotifications.class);
  private final UInt64 historicalBlockTolerance = UInt64.valueOf(5);
  private final UInt64 futureBlockTolerance = UInt64.valueOf(2);
  private final int maxPendingBlocks = 10;
  private final PendingPool<SignedBeaconBlock> pendingBlocks =
      PendingPool.createForBlocks(
          spec, historicalBlockTolerance, futureBlockTolerance, maxPendingBlocks);
  private final FutureItems<SignedBeaconBlock> futureBlocks =
      FutureItems.create(SignedBeaconBlock::getSlot);

  private final BlockImporter blockImporter = mock(BlockImporter.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StorageSystem remoteStorageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);

  private UInt64 currentSlot = GENESIS_SLOT;

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
          asyncRunner);

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = false;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = true;
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
    when(blockImporter.importBlock(nextBlock))
        .thenReturn(
            SafeFuture.completedFuture(
                BlockImportResult.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING));

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();

    // communication error
    when(blockImporter.importBlock(nextBlock))
        .thenReturn(
            SafeFuture.completedFuture(
                BlockImportResult.failedExecutionPayloadExecution(new RuntimeException("error"))));

    asyncRunner.executeQueuedActions();

    // successful imported now
    when(blockImporter.importBlock(nextBlock))
        .thenReturn(SafeFuture.completedFuture(BlockImportResult.successful(nextBlock)));

    asyncRunner.executeQueuedActions();

    verify(blockImporter, times(3)).importBlock(nextBlock);

    // should be dequeued now, so no more interactions
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  public void onInvalidBlockImport_shouldNotRetry() {
    final SignedBeaconBlock nextBlock =
        remoteStorageSystem.chainUpdater().chainBuilder.generateNextBlock().getBlock();

    // invalid
    when(blockImporter.importBlock(nextBlock))
        .thenReturn(
            SafeFuture.completedFuture(
                BlockImportResult.failedStateTransition(new IllegalStateException("invalid"))));

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();

    verify(blockImporter, times(1)).importBlock(nextBlock);

    // should not bw queued
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  public void onSlot_shouldDequeueExpired() {
    final SignedBeaconBlock nextBlock =
        remoteStorageSystem.chainUpdater().chainBuilder.generateNextBlock().getBlock();

    // syncing
    when(blockImporter.importBlock(nextBlock))
        .thenReturn(
            SafeFuture.completedFuture(
                BlockImportResult.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING));

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();

    verify(blockImporter, times(1)).importBlock(nextBlock);

    blockManager.onSlot(currentSlot.plus(3));

    // should be dequeued now, so no more interactions
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(blockImporter);
  }
}
