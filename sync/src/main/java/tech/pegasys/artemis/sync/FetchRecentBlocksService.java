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

import static tech.pegasys.artemis.util.async.FutureUtil.ignoreFuture;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.sync.FetchBlockTask.FetchBlockResult;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.events.Subscribers;

class FetchRecentBlocksService extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_CONCURRENT_REQUESTS = 3;
  private static final Duration WAIT_FOR_PEERS_DURATION = Duration.ofSeconds(5);
  private static final RetryDelayFunction DEFAULT_RETRY_DELAY_FUNCTION =
      RetryDelayFunction.createExponentialRetry(Duration.ofSeconds(5), Duration.ofMinutes(5));

  private final int maxConcurrentRequests;
  private final Eth2Network eth2Network;
  private final PendingPool<SignedBeaconBlock> pendingBlocksPool;

  private final Map<Bytes32, FetchBlockTask> allTasks = new ConcurrentHashMap<>();
  private final Queue<FetchBlockTask> pendingTasks = new ConcurrentLinkedQueue<>();
  private final Collection<FetchBlockTask> activeTasks = new ConcurrentLinkedQueue<>();

  private final FetchBlockTaskFactory fetchBlockTaskFactory;
  private final RetryDelayFunction retryDelayFunction;
  private final Subscribers<BlockSubscriber> blockSubscribers = Subscribers.create(true);
  private final ScheduledExecutorService scheduledExecutorService;

  FetchRecentBlocksService(
      final ScheduledExecutorService scheduledExecutorService,
      final Eth2Network eth2Network,
      final PendingPool<SignedBeaconBlock> pendingBlocksPool,
      final FetchBlockTaskFactory fetchBlockTaskFactory,
      final RetryDelayFunction retryDelayFunction,
      final int maxConcurrentRequests) {
    this.scheduledExecutorService = scheduledExecutorService;
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.eth2Network = eth2Network;
    this.pendingBlocksPool = pendingBlocksPool;
    this.fetchBlockTaskFactory = fetchBlockTaskFactory;
    this.retryDelayFunction = retryDelayFunction;
  }

  public static FetchRecentBlocksService create(
      final Eth2Network eth2Network, final PendingPool<SignedBeaconBlock> pendingBlocksPool) {
    return new FetchRecentBlocksService(
        new ScheduledThreadPoolExecutor(0),
        eth2Network,
        pendingBlocksPool,
        FetchBlockTask::create,
        DEFAULT_RETRY_DELAY_FUNCTION,
        MAX_CONCURRENT_REQUESTS);
  }

  @Override
  protected SafeFuture<?> doStart() {
    setupSubscribers();
    return SafeFuture.completedFuture(null);
  }

  @Override
  protected SafeFuture<?> doStop() {
    scheduledExecutorService.shutdownNow();
    return SafeFuture.completedFuture(null);
  }

  public long subscribeBlockFetched(final BlockSubscriber subscriber) {
    return blockSubscribers.subscribe(subscriber);
  }

  public void unsubscribeBlockFetched(final int subscriberId) {
    blockSubscribers.unsubscribe(subscriberId);
  }

  private void setupSubscribers() {
    this.pendingBlocksPool.subscribeRequiredBlockRoot(this::requestRecentBlock);
    this.pendingBlocksPool.subscribeRequiredBlockRootDropped(this::cancelRecentBlockRequest);
  }

  public void requestRecentBlock(final Bytes32 blockRoot) {
    if (pendingBlocksPool.contains(blockRoot)) {
      // We've already got this block
      return;
    }
    final FetchBlockTask task = fetchBlockTaskFactory.create(eth2Network, blockRoot);
    if (allTasks.putIfAbsent(blockRoot, task) != null) {
      // We're already tracking this task
      task.cancel();
      return;
    }
    LOG.trace("Queue block to be fetched: {}", blockRoot);
    queueTask(task);
  }

  public void cancelRecentBlockRequest(final Bytes32 blockRoot) {
    final FetchBlockTask task = allTasks.get(blockRoot);
    if (task != null) {
      task.cancel();
    }
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
        handleFetchedBlock(task, result.getBlock());
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

  private void registerActiveTask(FetchBlockTask task) {
    LOG.trace("Fetch block {}", task.getBlockRoot());
    activeTasks.add(task);
  }

  private void deregisterActiveTask(FetchBlockTask task) {
    activeTasks.remove(task);
    checkTasks();
  }

  private void removeTask(FetchBlockTask task) {
    // Stop tracking task
    task.cancel();
    allTasks.compute(
        task.getBlockRoot(),
        (root, existingTask) -> {
          if (Objects.equals(task, existingTask)) {
            return null;
          }
          return existingTask;
        });
  }

  private void queueTask(FetchBlockTask task) {
    pendingTasks.add(task);
    checkTasks();
  }

  private void queueTaskWithDelay(FetchBlockTask task, Duration delay) {
    ignoreFuture(
        scheduledExecutorService.schedule(
            () -> queueTask(task), delay.getSeconds(), TimeUnit.SECONDS));
  }

  private void queueTaskWithRetryDelay(final FetchBlockTask task) {
    final Duration delay = retryDelayFunction.getRetryDelay(task.getNumberOfRetries());
    queueTaskWithDelay(task, delay);
  }

  private void handleFetchedBlock(FetchBlockTask task, final SignedBeaconBlock block) {
    LOG.trace("Successfully fetched block: {}", block);
    blockSubscribers.forEach(s -> s.onBlock(block));
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

  interface FetchBlockTaskFactory {
    FetchBlockTask create(final Eth2Network eth2Network, final Bytes32 blockRoot);
  }

  public interface BlockSubscriber {
    void onBlock(SignedBeaconBlock block);
  }
}
