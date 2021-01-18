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

package tech.pegasys.teku.sync.gossip;

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
import org.mockito.invocation.InvocationOnMock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.gossip.FetchBlockTask.FetchBlockResult;
import tech.pegasys.teku.sync.gossip.FetchBlockTask.FetchBlockResult.Status;
import tech.pegasys.teku.sync.gossip.FetchRecentBlocksService.FetchBlockTaskFactory;

public class FetchRecentBlocksServiceTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Eth2Network eth2Network = mock(Eth2Network.class);

  @SuppressWarnings("unchecked")
  private final PendingPool<SignedBeaconBlock> pendingBlocksPool = mock(PendingPool.class);

  private final FetchBlockTaskFactory fetchBlockTaskFactory = mock(FetchBlockTaskFactory.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

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
            recentChainData,
            fetchBlockTaskFactory,
            maxConcurrentRequests);

    lenient()
        .when(fetchBlockTaskFactory.create(any(), any(), any()))
        .thenAnswer(this::createMockTask);
    recentBlockFetcher.subscribeBlockFetched(importedBlocks::add);
  }

  private FetchBlockTask createMockTask(final InvocationOnMock invocationOnMock) {
    final SlotAndBlockRoot targetChain = invocationOnMock.getArgument(1);
    Bytes32 blockRoot = invocationOnMock.getArgument(2);
    final FetchBlockTask task = mock(FetchBlockTask.class);

    lenient().when(task.getBlockRoot()).thenReturn(blockRoot);
    lenient().when(task.getNumberOfRetries()).thenReturn(0);
    lenient().when(task.getTargetChain()).thenReturn(targetChain);
    final SafeFuture<FetchBlockResult> future = new SafeFuture<>();
    lenient().when(task.run()).thenReturn(future);
    taskFutures.add(future);

    tasks.add(task);

    return task;
  }

  @Test
  public void fetchSingleBlockSuccessfully() {
    final SignedBeaconBlock childBlock = dataStructureUtil.randomSignedBeaconBlock(5);
    recentBlockFetcher.fetchAncestors(childBlock);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchBlockResult> future = taskFutures.get(0);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    when(recentChainData.containsBlock(block.getParentRoot())).thenReturn(true);
    future.complete(FetchBlockResult.createSuccessful(block));

    assertThat(importedBlocks).containsExactly(block);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  void immediatelyProcessBlockWhereParentBecomesAvailable() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    // Block is available by the time we get to processing it
    when(recentChainData.containsBlock(block.getParentRoot())).thenReturn(true);
    recentBlockFetcher.fetchAncestors(block);

    assertThat(importedBlocks).containsExactly(block);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handleDuplicateRequiredBlocks() {
    final SignedBeaconBlock childBlock1 = dataStructureUtil.randomSignedBeaconBlock(5);
    final SignedBeaconBlock childBlock2 =
        dataStructureUtil.randomSignedBeaconBlock(7, childBlock1.getParentRoot());
    recentBlockFetcher.fetchAncestors(childBlock1);
    recentBlockFetcher.fetchAncestors(childBlock2);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchBlockResult> future = taskFutures.get(0);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    when(recentChainData.containsBlock(block.getParentRoot())).thenReturn(true);
    future.complete(FetchBlockResult.createSuccessful(block));

    assertThat(importedBlocks).containsExactly(block);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void ignoreKnownBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    when(pendingBlocksPool.contains(block.getParentRoot())).thenReturn(true);
    recentBlockFetcher.fetchAncestors(block);

    assertTaskCounts(0, 0, 0);
    assertThat(importedBlocks).isEmpty();
  }

  @Test
  public void cancelBlockRequest() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    recentBlockFetcher.fetchAncestors(block);
    recentBlockFetcher.cancelRecentBlockRequest(block.getParentRoot());

    verify(tasks.get(0)).cancel();
    // Manually cancel future
    taskFutures.get(0).complete(FetchBlockResult.createFailed(Status.CANCELLED));

    // Task should be removed
    assertTaskCounts(0, 0, 0);
    assertThat(importedBlocks).isEmpty();
  }

  @Test
  public void fetchSingleBlockWithRetry() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    recentBlockFetcher.fetchAncestors(block);

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
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    recentBlockFetcher.fetchAncestors(block);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlocks).isEmpty();

    final SafeFuture<FetchBlockResult> future = taskFutures.get(0);
    future.complete(FetchBlockResult.createFailed(Status.FETCH_FAILED));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.get(0)).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Cancel task
    recentBlockFetcher.cancelRecentBlockRequest(block.getParentRoot());
    verify(tasks.get(0)).cancel();
    when(tasks.get(0).run())
        .thenReturn(SafeFuture.completedFuture(FetchBlockResult.createFailed(Status.CANCELLED)));

    // Executor should requeue task, it should complete immediately and be removed
    asyncRunner.executeQueuedActions();
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handlesPeersUnavailable() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    recentBlockFetcher.fetchAncestors(block);

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
      final SignedBeaconBlock childBlock = dataStructureUtil.randomSignedBeaconBlock(5);
      recentBlockFetcher.fetchAncestors(childBlock);
    }

    assertTaskCounts(taskCount, taskCount - 1, 1);

    // Complete first task
    final SafeFuture<FetchBlockResult> future = taskFutures.get(0);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    when(recentChainData.containsBlock(block.getParentRoot())).thenReturn(true);
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
