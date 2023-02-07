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

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlockResult;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlockTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlockTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.singlepeer.RetryDelayFunction;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.statetransition.util.PendingPool;

public class FetchRecentBlocksService extends Service
    implements RecentBlockFetcherService, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_CONCURRENT_REQUESTS = 3;
  private static final Duration WAIT_FOR_PEERS_DURATION = Duration.ofSeconds(30);
  private static final RetryDelayFunction RETRY_DELAY_FUNCTION =
      RetryDelayFunction.createExponentialRetry(2, Duration.ofSeconds(5), Duration.ofMinutes(5));

  private final ForwardSync forwardSync;
  private final int maxConcurrentRequests;
  private final PendingPool<SignedBeaconBlock> pendingBlocksPool;

  private final Map<Bytes32, FetchBlockTask> allTasks = new ConcurrentHashMap<>();
  private final Queue<FetchBlockTask> pendingTasks = new ConcurrentLinkedQueue<>();
  private final Collection<FetchBlockTask> activeTasks = new ConcurrentLinkedQueue<>();

  private final FetchBlockTaskFactory fetchBlockTaskFactory;
  private final Subscribers<BlockSubscriber> blockSubscribers = Subscribers.create(true);
  private final AsyncRunner asyncRunner;

  private volatile UInt64 currentSlot = UInt64.ZERO;

  FetchRecentBlocksService(
      final AsyncRunner asyncRunner,
      final PendingPool<SignedBeaconBlock> pendingBlocksPool,
      final ForwardSync forwardSync,
      final FetchBlockTaskFactory fetchBlockTaskFactory,
      final int maxConcurrentRequests) {
    this.asyncRunner = asyncRunner;
    this.forwardSync = forwardSync;
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.pendingBlocksPool = pendingBlocksPool;
    this.fetchBlockTaskFactory = fetchBlockTaskFactory;
  }

  public static FetchRecentBlocksService create(
      final AsyncRunner asyncRunner,
      final PendingPool<SignedBeaconBlock> pendingBlocksPool,
      final ForwardSync forwardSync,
      final FetchBlockTaskFactory fetchBlockTaskFactory) {
    return new FetchRecentBlocksService(
        asyncRunner,
        pendingBlocksPool,
        forwardSync,
        fetchBlockTaskFactory,
        MAX_CONCURRENT_REQUESTS);
  }

  @Override
  protected SafeFuture<?> doStart() {
    setupSubscribers();
    return SafeFuture.completedFuture(null);
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.completedFuture(null);
  }

  @Override
  public long subscribeBlockFetched(final BlockSubscriber subscriber) {
    return blockSubscribers.subscribe(subscriber);
  }

  private void setupSubscribers() {
    this.pendingBlocksPool.subscribeRequiredBlockRoot(this::requestRecentBlock);
    this.pendingBlocksPool.subscribeRequiredBlockRootDropped(this::cancelRecentBlockRequest);
    forwardSync.subscribeToSyncChanges(this::onSyncStatusChanged);
  }

  private void onSyncStatusChanged(final boolean syncActive) {
    if (syncActive) {
      return;
    }
    // Ensure we are requesting the parents of any pending blocks not already filled in by the sync
    // We may have ignored these requested blocks while the sync was in progress
    pendingBlocksPool.getAllRequiredBlockRoots().forEach(this::requestRecentBlock);
  }

  @Override
  public void requestRecentBlock(final Bytes32 blockRoot) {
    if (forwardSync.isSyncActive()) {
      // Forward sync already in progress, assume it will fetch any missing blocks
      return;
    }
    if (pendingBlocksPool.contains(blockRoot)) {
      // We've already got this block
      return;
    }
    final FetchBlockTask task = fetchBlockTaskFactory.create(currentSlot, blockRoot);
    if (allTasks.putIfAbsent(blockRoot, task) != null) {
      // We're already tracking this task
      task.cancel();
      return;
    }
    LOG.trace("Queue block to be fetched: {}", blockRoot);
    queueTask(task);
  }

  @Override
  public void cancelRecentBlockRequest(final Bytes32 blockRoot) {
    final FetchBlockTask task = allTasks.get(blockRoot);
    if (task != null) {
      task.cancel();
    }
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot = slot;
  }

  private synchronized void checkTasks() {
    // If we have capacity, execute the next task
    if (activeTasks.size() < maxConcurrentRequests) {
      final FetchBlockTask nextTask = pendingTasks.poll();
      if (nextTask == null) {
        return;
      }
      runNextTask(nextTask);
    }
  }

  private void runNextTask(final FetchBlockTask task) {
    registerActiveTask(task);
    task.run()
        .thenAccept(res -> processFetchResult(task, res))
        .exceptionally(
            (err) -> {
              LOG.warn("Failed to run " + task.getClass().getSimpleName(), err);
              return null;
            })
        .always(() -> deregisterActiveTask(task));
  }

  private void processFetchResult(final FetchBlockTask task, final FetchBlockResult result) {
    switch (result.getStatus()) {
      case SUCCESSFUL:
        handleFetchedBlock(task, result.getBlock().orElseThrow(), result.getBlobsSidecar());
        break;
      case NO_AVAILABLE_PEERS:
        // Wait a bit and then requeue
        queueTaskWithDelay(task, WAIT_FOR_PEERS_DURATION);
        break;
      case FETCH_FAILED:
        // Push task back onto queue to retry
        queueTaskWithRetryDelay(task);
        break;
      case CANCELLED:
        LOG.trace("Request for block cancelled: {}.", task.getBlockRoot());
        removeTask(task);
        break;
    }
  }

  private void registerActiveTask(final FetchBlockTask task) {
    LOG.trace("Fetch block {}", task.getBlockRoot());
    activeTasks.add(task);
  }

  private void deregisterActiveTask(final FetchBlockTask task) {
    activeTasks.remove(task);
    checkTasks();
  }

  private void removeTask(final FetchBlockTask task) {
    // Stop tracking task
    task.cancel();
    allTasks.remove(task.getBlockRoot(), task);
  }

  private void queueTask(final FetchBlockTask task) {
    pendingTasks.add(task);
    checkTasks();
  }

  private void queueTaskWithDelay(final FetchBlockTask task, final Duration delay) {
    asyncRunner
        .getDelayedFuture(delay)
        .finish(
            () -> queueTask(task),
            (err) -> {
              task.cancel();
              LOG.error(
                  "Unable to execute delayed task.  Dropping task to fetch block "
                      + task.getBlockRoot(),
                  err);
            });
  }

  private void queueTaskWithRetryDelay(final FetchBlockTask task) {
    final Duration delay = RETRY_DELAY_FUNCTION.getRetryDelay(task.getNumberOfRetries());
    queueTaskWithDelay(task, delay);
  }

  private void handleFetchedBlock(
      final FetchBlockTask task,
      final SignedBeaconBlock block,
      final Optional<BlobsSidecar> blobsSidecar) {
    LOG.trace("Successfully fetched block: {}", block);
    blockSubscribers.forEach(s -> s.onBlockAndBlobsSidecar(block, blobsSidecar));
    // After retrieved block has been processed, stop tracking it
    removeTask(task);
  }

  @VisibleForTesting
  int countPendingTasks() {
    return pendingTasks.size();
  }

  @VisibleForTesting
  int countActiveTasks() {
    return activeTasks.size();
  }

  @VisibleForTesting
  int countTrackedTasks() {
    return allTasks.size();
  }
}
