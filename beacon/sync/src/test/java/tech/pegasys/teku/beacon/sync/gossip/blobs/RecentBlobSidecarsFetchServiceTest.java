/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.beacon.sync.gossip.blobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import tech.pegasys.teku.beacon.sync.fetch.BlockRootAndBlobIdentifiers;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlobSidecarsTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;

class RecentBlobSidecarsFetchServiceTest {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool =
      mock(BlockBlobSidecarsTrackersPool.class);

  private final FetchTaskFactory fetchTaskFactory = mock(FetchTaskFactory.class);

  private final ForwardSync forwardSync = mock(ForwardSync.class);

  private final int maxConcurrentRequests = 2;

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final List<FetchBlobSidecarsTask> tasks = new ArrayList<>();
  private final List<SafeFuture<FetchResult<List<BlobSidecar>>>> taskFutures = new ArrayList<>();
  private final List<BlobSidecar> importedBlobSidecars = new ArrayList<>();

  private RecentBlobSidecarsFetchService recentBlobSidecarsFetcher;

  @BeforeEach
  public void setup() {
    recentBlobSidecarsFetcher =
        new RecentBlobSidecarsFetchService(
            asyncRunner,
            blockBlobSidecarsTrackersPool,
            forwardSync,
            fetchTaskFactory,
            maxConcurrentRequests);

    lenient()
        .when(fetchTaskFactory.createFetchBlobSidecarsTask(any()))
        .thenAnswer(this::createMockTask);
    recentBlobSidecarsFetcher.subscribeBlobSidecarFetched(importedBlobSidecars::add);
  }

  @Test
  public void fetchSingleBlobSidecarSuccessfully() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();

    recentBlobSidecarsFetcher.requestRecentBlobSidecars(
        blobSidecar.getBlockRoot(),
        List.of(new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex())));

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<List<BlobSidecar>>> future = taskFutures.getFirst();
    future.complete(FetchResult.createSuccessful(List.of(blobSidecar)));

    assertThat(importedBlobSidecars).containsExactly(blobSidecar);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void fetchMultipleBlobSidecarsSuccessfully() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithCommitments(3);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);

    recentBlobSidecarsFetcher.requestRecentBlobSidecars(
        block.getRoot(),
        blobSidecars.stream()
            .map(sidecar -> new BlobIdentifier(sidecar.getBlockRoot(), sidecar.getIndex()))
            .toList());

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<List<BlobSidecar>>> future = taskFutures.getFirst();
    future.complete(FetchResult.createSuccessful(blobSidecars));

    assertThat(importedBlobSidecars).containsExactlyElementsOf(blobSidecars);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handleRequiredBlobSidecarsWithSameBlockAndBlobIdentifier() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final Bytes32 blockRoot = blobSidecar.getBlockRoot();
    final BlobIdentifier blobIdentifier = dataStructureUtil.randomBlobIdentifier(blockRoot);

    recentBlobSidecarsFetcher.requestRecentBlobSidecars(blockRoot, List.of(blobIdentifier));
    recentBlobSidecarsFetcher.requestRecentBlobSidecars(blockRoot, List.of(blobIdentifier));

    // only one task allowed per block and blob identifiers
    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<List<BlobSidecar>>> future = taskFutures.getFirst();
    future.complete(FetchResult.createSuccessful(List.of(blobSidecar)));

    assertThat(importedBlobSidecars).containsExactly(blobSidecar);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void ignoreIfNoBlobSidecarsAreRequired() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final BlobIdentifier blobIdentifier = dataStructureUtil.randomBlobIdentifier(blockRoot);
    final BlobIdentifier anotherBlobIdentifier = dataStructureUtil.randomBlobIdentifier(blockRoot);
    when(blockBlobSidecarsTrackersPool.containsBlobSidecar(any())).thenReturn(true);
    recentBlobSidecarsFetcher.requestRecentBlobSidecars(
        blockRoot, List.of(blobIdentifier, anotherBlobIdentifier));

    assertTaskCounts(0, 0, 0);
    assertThat(importedBlobSidecars).isEmpty();
  }

  @Test
  public void cancelBlobSidecarsRequests() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final BlobIdentifier blobIdentifier = dataStructureUtil.randomBlobIdentifier(blockRoot);
    final BlobIdentifier anotherBlobIdentifier = dataStructureUtil.randomBlobIdentifier(blockRoot);

    recentBlobSidecarsFetcher.requestRecentBlobSidecars(blockRoot, List.of(blobIdentifier));
    recentBlobSidecarsFetcher.requestRecentBlobSidecars(blockRoot, List.of(anotherBlobIdentifier));

    assertTaskCounts(2, 2, 0);

    recentBlobSidecarsFetcher.cancelRecentBlobSidecarsRequests(blockRoot);

    tasks.forEach(task -> verify(task).cancel());
    // manually cancel futures
    taskFutures.forEach(
        taskFuture -> taskFuture.complete(FetchResult.createFailed(Status.CANCELLED)));

    // Tasks should be removed
    assertTaskCounts(0, 0, 0);
    assertThat(importedBlobSidecars).isEmpty();
  }

  @Test
  public void fetchSingleBlobSidecarWithRetry() {
    recentBlobSidecarsFetcher.requestRecentBlobSidecars(
        dataStructureUtil.randomBytes32(), dataStructureUtil.randomBlobIdentifiers(2));

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<List<BlobSidecar>>> future = taskFutures.getFirst();
    future.complete(FetchResult.createFailed(Status.FETCH_FAILED));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.getFirst()).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Executor should requeue task
    when(tasks.getFirst().run()).thenReturn(new SafeFuture<>());
    asyncRunner.executeQueuedActions();
    assertTaskCounts(1, 1, 0);
  }

  @Test
  public void cancelTaskWhileWaitingToRetry() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final List<BlobIdentifier> blobIdentifiers =
        List.of(new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex()));
    recentBlobSidecarsFetcher.requestRecentBlobSidecars(
        blobSidecar.getBlockRoot(), blobIdentifiers);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<List<BlobSidecar>>> future = taskFutures.getFirst();
    future.complete(FetchResult.createFailed(Status.FETCH_FAILED));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.getFirst()).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Cancel task
    recentBlobSidecarsFetcher.cancelRecentBlobSidecarsRequests(blobSidecar.getBlockRoot());
    verify(tasks.getFirst()).cancel();
    when(tasks.getFirst().run())
        .thenReturn(SafeFuture.completedFuture(FetchResult.createFailed(Status.CANCELLED)));

    // Executor should requeue task, it should complete immediately and be removed
    asyncRunner.executeQueuedActions();
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handlesPeersUnavailable() {
    recentBlobSidecarsFetcher.requestRecentBlobSidecars(
        dataStructureUtil.randomBytes32(), dataStructureUtil.randomBlobIdentifiers(2));

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<List<BlobSidecar>>> future = taskFutures.getFirst();
    future.complete(FetchResult.createFailed(Status.NO_AVAILABLE_PEERS));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.getFirst(), never()).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Executor should requeue task
    when(tasks.getFirst().run()).thenReturn(new SafeFuture<>());
    asyncRunner.executeQueuedActions();
    assertTaskCounts(1, 1, 0);
  }

  @Test
  public void queueFetchTaskWhenConcurrencyLimitReached() {
    final int taskCount = maxConcurrentRequests + 1;
    for (int i = 0; i < taskCount; i++) {
      recentBlobSidecarsFetcher.requestRecentBlobSidecars(
          dataStructureUtil.randomBytes32(), dataStructureUtil.randomBlobIdentifiers(2));
    }

    assertTaskCounts(taskCount, taskCount - 1, 1);

    // Complete first task
    final SafeFuture<FetchResult<List<BlobSidecar>>> future = taskFutures.getFirst();
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    future.complete(FetchResult.createSuccessful(List.of(blobSidecar)));

    // After first task completes, remaining pending count should become active
    assertTaskCounts(taskCount - 1, taskCount - 1, 0);
  }

  @Test
  void shouldNotFetchBlobSidecarsWhileForwardSyncIsInProgress() {
    when(forwardSync.isSyncActive()).thenReturn(true);

    recentBlobSidecarsFetcher.requestRecentBlobSidecars(
        dataStructureUtil.randomBytes32(), dataStructureUtil.randomBlobIdentifiers(2));
    assertTaskCounts(0, 0, 0);
  }

  @Test
  void shouldRequestRemainingRequiredBlobSidecarsWhenForwardSyncCompletes() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final Map<Bytes32, List<BlobIdentifier>> requiredBlobIdentifiers =
        Map.of(
            blockRoot,
            List.of(dataStructureUtil.randomBlobIdentifier(blockRoot)),
            blockRoot1,
            List.of(dataStructureUtil.randomBlobIdentifier(blockRoot1)));
    when(blockBlobSidecarsTrackersPool.getAllRequiredBlobSidecars())
        .thenReturn(requiredBlobIdentifiers);

    final ArgumentCaptor<SyncSubscriber> syncListenerCaptor =
        ArgumentCaptor.forClass(SyncSubscriber.class);
    assertThat(recentBlobSidecarsFetcher.start()).isCompleted();
    verify(forwardSync).subscribeToSyncChanges(syncListenerCaptor.capture());
    final SyncSubscriber syncSubscriber = syncListenerCaptor.getValue();

    syncSubscriber.onSyncingChange(false);
    assertTaskCounts(2, 2, 0);
    final List<Bytes32> requestingBlockRoots =
        tasks.stream().map(task -> task.getKey().blockRoot()).toList();
    assertThat(requestingBlockRoots).containsExactlyInAnyOrder(blockRoot, blockRoot1);
  }

  private FetchBlobSidecarsTask createMockTask(final InvocationOnMock invocationOnMock) {
    final BlockRootAndBlobIdentifiers blockRootAndBlobIdentifiers = invocationOnMock.getArgument(0);
    final FetchBlobSidecarsTask task = mock(FetchBlobSidecarsTask.class);

    lenient().when(task.getKey()).thenReturn(blockRootAndBlobIdentifiers);
    lenient().when(task.getNumberOfRetries()).thenReturn(0);
    final SafeFuture<FetchResult<List<BlobSidecar>>> future = new SafeFuture<>();
    lenient().when(task.run()).thenReturn(future);
    taskFutures.add(future);
    tasks.add(task);

    return task;
  }

  private void assertTaskCounts(
      final int totalTasks, final int activeTasks, final int queuedTasks) {
    assertThat(recentBlobSidecarsFetcher.countTrackedTasks())
        .describedAs("Tracked tasks")
        .isEqualTo(totalTasks);
    assertThat(recentBlobSidecarsFetcher.countActiveTasks())
        .describedAs("Active tasks")
        .isEqualTo(activeTasks);
    assertThat(recentBlobSidecarsFetcher.countPendingTasks())
        .describedAs("Pending tasks")
        .isEqualTo(queuedTasks);
  }
}
