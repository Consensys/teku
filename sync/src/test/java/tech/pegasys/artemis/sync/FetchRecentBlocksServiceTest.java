/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.sync.FetchBlockTask.FetchBlockResult;
import tech.pegasys.artemis.sync.FetchBlockTask.FetchBlockResult.Status;
import tech.pegasys.artemis.sync.FetchRecentBlocksService.FetchBlockTaskFactory;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

@ExtendWith(MockitoExtension.class)
public class FetchRecentBlocksServiceTest {

  @Mock private Eth2Network eth2Network;
  @Mock private PendingPool<SignedBeaconBlock> pendingBlocksPool;
  @Mock private FetchBlockTaskFactory fetchBlockTaskFactory;

  private final int maxConcurrentRequests = 2;
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final List<FetchBlockTask> tasks = new ArrayList<>();
  private final List<SafeFuture<FetchBlockResult>> taskFutures = new ArrayList<>();
  private final List<SignedBeaconBlock> importedBlocks = new ArrayList<>();

  private FetchRecentBlocksService recentBlockFetcher;

  @BeforeEach
  public void setup() {
    recentBlockFetcher =
        new FetchRecentBlocksService(
            asyncRunner,
            eth2Network,
            pendingBlocksPool,
            fetchBlockTaskFactory,
            maxConcurrentRequests);

    lenient().when(fetchBlockTaskFactory.create(any(), any())).thenAnswer(this::createMockTask);
    recentBlockFetcher.subscribeBlockFetched(importedBlocks::add);
  }

  private FetchBlockTask createMockTask(final InvocationOnMock invocationOnMock) {
    Bytes32 blockRoot = invocationOnMock.getArgument(1);
    final FetchBlockTask task = mock(FetchBlockTask.class);

    lenient().when(task.getBlockRoot()).thenReturn(blockRoot);
    lenient().when(task.getNumberOfRetries()).thenReturn(0);
    final SafeFuture<FetchBlockResult> future = new SafeFuture<>();
    lenient().when(task.run()).thenReturn(future);
    taskFutures.add(future);

    tasks.add(task);

    return task;
  }

  @Test
  public void fetchSingleBlockSuccessfully() {
    final Bytes32 root = DataStructureUtil.randomBytes32(1);
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchBlockResult> future = taskFutures.get(0);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(1, 1);
    future.complete(FetchBlockResult.createSuccessful(block));

    assertThat(importedBlocks).containsExactly(block);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handleDuplicateRequiredBlocks() {
    final Bytes32 root = DataStructureUtil.randomBytes32(1);
    recentBlockFetcher.requestRecentBlock(root);
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchBlockResult> future = taskFutures.get(0);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(1, 1);
    future.complete(FetchBlockResult.createSuccessful(block));

    assertThat(importedBlocks).containsExactly(block);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void ignoreKnownBlock() {
    final Bytes32 root = DataStructureUtil.randomBytes32(1);
    when(pendingBlocksPool.contains(root)).thenReturn(true);
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(0, 0, 0);
    assertThat(importedBlocks).isEmpty();
  }

  @Test
  public void cancelBlockRequest() {
    final Bytes32 root = DataStructureUtil.randomBytes32(1);
    recentBlockFetcher.requestRecentBlock(root);
    recentBlockFetcher.cancelRecentBlockRequest(root);

    verify(tasks.get(0)).cancel();
    // Manually cancel future
    taskFutures.get(0).complete(FetchBlockResult.createFailed(Status.CANCELLED));

    // Task should be removed
    assertTaskCounts(0, 0, 0);
    assertThat(importedBlocks).isEmpty();
  }

  @Test
  public void fetchSingleBlockWithRetry() {
    final Bytes32 root = DataStructureUtil.randomBytes32(1);
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchBlockResult> future = taskFutures.get(0);
    future.complete(FetchBlockResult.createFailed(Status.FETCH_FAILED));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.get(0)).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Executor should requeue task
    when(tasks.get(0).run()).thenReturn(new SafeFuture<>());
    asyncRunner.executeQueuedActions();
    assertTaskCounts(1, 1, 0);
  }

  @Test
  public void cancelTaskWhileWaitingToRetry() {
    final Bytes32 root = DataStructureUtil.randomBytes32(1);
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchBlockResult> future = taskFutures.get(0);
    future.complete(FetchBlockResult.createFailed(Status.FETCH_FAILED));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.get(0)).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Cancel task
    recentBlockFetcher.cancelRecentBlockRequest(root);
    verify(tasks.get(0)).cancel();
    when(tasks.get(0).run())
        .thenReturn(SafeFuture.completedFuture(FetchBlockResult.createFailed(Status.CANCELLED)));

    // Executor should requeue task, it should complete immediately and be removed
    asyncRunner.executeQueuedActions();
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handlesPeersUnavailable() {
    final Bytes32 root = DataStructureUtil.randomBytes32(1);
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchBlockResult> future = taskFutures.get(0);
    future.complete(FetchBlockResult.createFailed(Status.NO_AVAILABLE_PEERS));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.get(0), never()).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Executor should requeue task
    when(tasks.get(0).run()).thenReturn(new SafeFuture<>());
    asyncRunner.executeQueuedActions();
    assertTaskCounts(1, 1, 0);
  }

  @Test
  public void queueFetchTaskWhenConcurrencyLimitReached() {
    final int taskCount = maxConcurrentRequests + 1;
    for (int i = 0; i < taskCount; i++) {
      final Bytes32 root = DataStructureUtil.randomBytes32(1);
      recentBlockFetcher.requestRecentBlock(root);
    }

    assertTaskCounts(taskCount, taskCount - 1, 1);

    // Complete first task
    final SafeFuture<FetchBlockResult> future = taskFutures.get(0);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(1, 1);
    future.complete(FetchBlockResult.createSuccessful(block));

    // After first task completes, remaining pending count should become active
    assertTaskCounts(taskCount - 1, taskCount - 1, 0);
  }

  private void assertTaskCounts(
      final int totalTasks, final int activeTasks, final int queuedTasks) {
    assertThat(recentBlockFetcher.countTrackedTasks()).isEqualTo(totalTasks);
    assertThat(recentBlockFetcher.countActiveTasks()).isEqualTo(activeTasks);
    assertThat(recentBlockFetcher.countPendingTasks()).isEqualTo(queuedTasks);
  }
}
