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

package tech.pegasys.teku.beacon.sync.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlockTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.PendingPool;

public class FetchRecentBlocksServiceTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @SuppressWarnings("unchecked")
  private final PendingPool<SignedBeaconBlock> pendingBlocksPool = mock(PendingPool.class);

  private final FetchTaskFactory fetchTaskFactory = mock(FetchTaskFactory.class);

  private final ForwardSync forwardSync = mock(ForwardSync.class);

  private final int maxConcurrentRequests = 2;
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final List<FetchBlockTask> tasks = new ArrayList<>();
  private final List<SafeFuture<FetchResult<SignedBeaconBlock>>> taskFutures = new ArrayList<>();
  private final List<SignedBeaconBlock> importedBlocks = new ArrayList<>();

  private FetchRecentBlocksService recentBlockFetcher;

  @BeforeEach
  public void setup() {
    recentBlockFetcher =
        new FetchRecentBlocksService(
            asyncRunner, pendingBlocksPool, forwardSync, fetchTaskFactory, maxConcurrentRequests);

    lenient().when(fetchTaskFactory.createFetchBlockTask(any())).thenAnswer(this::createMockTask);
    recentBlockFetcher.subscribeBlockFetched(importedBlocks::add);
  }

  private FetchBlockTask createMockTask(final InvocationOnMock invocationOnMock) {
    Bytes32 blockRoot = invocationOnMock.getArgument(0);
    final FetchBlockTask task = mock(FetchBlockTask.class);

    lenient().when(task.getBlockRoot()).thenReturn(blockRoot);
    lenient().when(task.getNumberOfRetries()).thenReturn(0);
    final SafeFuture<FetchResult<SignedBeaconBlock>> future = new SafeFuture<>();
    lenient().when(task.run()).thenReturn(future);
    taskFutures.add(future);

    tasks.add(task);

    return task;
  }

  @Test
  public void fetchSingleBlockSuccessfully() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchResult<SignedBeaconBlock>> future = taskFutures.get(0);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    future.complete(FetchResult.createSuccessful(block));

    assertThat(importedBlocks).containsExactly(block);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handleDuplicateRequiredBlocks() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    recentBlockFetcher.requestRecentBlock(root);
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchResult<SignedBeaconBlock>> future = taskFutures.get(0);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    future.complete(FetchResult.createSuccessful(block));

    assertThat(importedBlocks).containsExactly(block);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void ignoreKnownBlock() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    when(pendingBlocksPool.contains(root)).thenReturn(true);
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(0, 0, 0);
    assertThat(importedBlocks).isEmpty();
  }

  @Test
  public void cancelBlockRequest() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    recentBlockFetcher.requestRecentBlock(root);
    recentBlockFetcher.cancelRecentBlockRequest(root);

    verify(tasks.get(0)).cancel();
    // Manually cancel future
    taskFutures.get(0).complete(FetchResult.createFailed(Status.CANCELLED));

    // Task should be removed
    assertTaskCounts(0, 0, 0);
    assertThat(importedBlocks).isEmpty();
  }

  @Test
  public void fetchSingleBlockWithRetry() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchResult<SignedBeaconBlock>> future = taskFutures.get(0);
    future.complete(FetchResult.createFailed(Status.FETCH_FAILED));

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
    final Bytes32 root = dataStructureUtil.randomBytes32();
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchResult<SignedBeaconBlock>> future = taskFutures.get(0);
    future.complete(FetchResult.createFailed(Status.FETCH_FAILED));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.get(0)).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Cancel task
    recentBlockFetcher.cancelRecentBlockRequest(root);
    verify(tasks.get(0)).cancel();
    when(tasks.get(0).run())
        .thenReturn(SafeFuture.completedFuture(FetchResult.createFailed(Status.CANCELLED)));

    // Executor should requeue task, it should complete immediately and be removed
    asyncRunner.executeQueuedActions();
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handlesPeersUnavailable() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    recentBlockFetcher.requestRecentBlock(root);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchResult<SignedBeaconBlock>> future = taskFutures.get(0);
    future.complete(FetchResult.createFailed(Status.NO_AVAILABLE_PEERS));

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
      final Bytes32 root = dataStructureUtil.randomBytes32();
      recentBlockFetcher.requestRecentBlock(root);
    }

    assertTaskCounts(taskCount, taskCount - 1, 1);

    // Complete first task
    final SafeFuture<FetchResult<SignedBeaconBlock>> future = taskFutures.get(0);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    future.complete(FetchResult.createSuccessful(block));

    // After first task completes, remaining pending count should become active
    assertTaskCounts(taskCount - 1, taskCount - 1, 0);
  }

  @Test
  void shouldNotFetchBlocksWhileForwardSyncIsInProgress() {
    when(forwardSync.isSyncActive()).thenReturn(true);

    recentBlockFetcher.requestRecentBlock(dataStructureUtil.randomBytes32());
    assertTaskCounts(0, 0, 0);
  }

  @Test
  void shouldRequestRemainingRequiredBlocksWhenForwardSyncCompletes() {
    final Set<Bytes32> requiredRoots =
        Set.of(dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32());
    when(pendingBlocksPool.getAllRequiredBlockRoots()).thenReturn(requiredRoots);

    final ArgumentCaptor<SyncSubscriber> syncListenerCaptor =
        ArgumentCaptor.forClass(SyncSubscriber.class);
    assertThat(recentBlockFetcher.start()).isCompleted();
    verify(forwardSync).subscribeToSyncChanges(syncListenerCaptor.capture());
    final SyncSubscriber syncSubscriber = syncListenerCaptor.getValue();

    syncSubscriber.onSyncingChange(false);
    assertTaskCounts(2, 2, 0);
    final Set<Bytes32> requestingRoots =
        tasks.stream().map(FetchBlockTask::getBlockRoot).collect(Collectors.toSet());
    assertThat(requestingRoots).containsExactlyInAnyOrderElementsOf(requestingRoots);
  }

  private void assertTaskCounts(
      final int totalTasks, final int activeTasks, final int queuedTasks) {
    assertThat(recentBlockFetcher.countTrackedTasks())
        .describedAs("Tracked tasks")
        .isEqualTo(totalTasks);
    assertThat(recentBlockFetcher.countActiveTasks())
        .describedAs("Active tasks")
        .isEqualTo(activeTasks);
    assertThat(recentBlockFetcher.countPendingTasks())
        .describedAs("Pending tasks")
        .isEqualTo(queuedTasks);
  }
}
