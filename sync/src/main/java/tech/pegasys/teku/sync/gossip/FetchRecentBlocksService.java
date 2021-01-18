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

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.forward.singlepeer.RetryDelayFunction;
import tech.pegasys.teku.sync.gossip.FetchBlockTask.FetchBlockResult;

public class FetchRecentBlocksService extends Service implements RecentBlockFetcherService {
  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_CONCURRENT_REQUESTS = 3;
  private static final Duration WAIT_FOR_PEERS_DURATION = Duration.ofSeconds(30);
  private static final RetryDelayFunction RETRY_DELAY_FUNCTION =
      RetryDelayFunction.createExponentialRetry(2, Duration.ofSeconds(5), Duration.ofMinutes(5));

  private final RecentChainData recentChainData;
  private final int maxConcurrentRequests;
  private final P2PNetwork<Eth2Peer> eth2Network;
  private final PendingPool<SignedBeaconBlock> pendingBlocksPool;

  private final Map<Bytes32, FetchBlockTask> allTasks = new ConcurrentHashMap<>();
  private final Queue<FetchBlockTask> pendingTasks = new ConcurrentLinkedQueue<>();
  private final Collection<FetchBlockTask> activeTasks = new ConcurrentLinkedQueue<>();

  private final FetchBlockTaskFactory fetchBlockTaskFactory;
  private final Subscribers<BlockSubscriber> blockSubscribers = Subscribers.create(true);
  private final AsyncRunner asyncRunner;

  FetchRecentBlocksService(
      final AsyncRunner asyncRunner,
      final P2PNetwork<Eth2Peer> eth2Network,
      final PendingPool<SignedBeaconBlock> pendingBlocksPool,
      final RecentChainData recentChainData,
      final FetchBlockTaskFactory fetchBlockTaskFactory,
      final int maxConcurrentRequests) {
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.eth2Network = eth2Network;
    this.pendingBlocksPool = pendingBlocksPool;
    this.fetchBlockTaskFactory = fetchBlockTaskFactory;
  }

  public static FetchRecentBlocksService create(
      final AsyncRunner asyncRunner,
      final P2PNetwork<Eth2Peer> eth2Network,
      final PendingPool<SignedBeaconBlock> pendingBlocksPool,
      final RecentChainData recentChainData) {
    return new FetchRecentBlocksService(
        asyncRunner,
        eth2Network,
        pendingBlocksPool,
        recentChainData,
        FetchBlockTask::create,
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
  public void subscribeBlockFetched(final BlockSubscriber subscriber) {
    blockSubscribers.subscribe(subscriber);
  }

  @Override
  public void fetchAncestors(final SignedBeaconBlock block) {
    fetchAncestors(block, new SlotAndBlockRoot(block.getSlot(), block.getRoot()));
  }

  public void fetchAncestors(final SignedBeaconBlock block, final SlotAndBlockRoot targetChain) {
    if (pendingBlocksPool.contains(block.getRoot())) {
      // Already got this block, no need to do anything
      return;
    }
    // TODO: If the block is too far from current chain head, or too far from the target chain head,
    //  ask the batch sync to sync the fork
    pendingBlocksPool.add(block);
    // Check if the parent was imported on another thread and if so, remove from the pendingPool
    // again and process now. We must add the block to the pending pool before this check happens to
    // avoid race conditions between performing the check and the parent importing.
    if (recentChainData.containsBlock(block.getParentRoot())) {
      pendingBlocksPool.remove(block);
      blockSubscribers.deliver(BlockSubscriber::onBlock, block);
      return;
    }
    if (pendingBlocksPool.contains(block.getParentRoot())) {
      // We've already got this block's parent, no need to request it
      return;
    }
    requestBlockParent(block, targetChain);
  }

  private void setupSubscribers() {
    this.pendingBlocksPool.subscribePendingItemDropped(this::notifyBlockReceived);
  }

  private void requestBlockParent(
      final SignedBeaconBlock block, final SlotAndBlockRoot targetChain) {
    final Bytes32 blockRoot = block.getParentRoot();

    final FetchBlockTask task = fetchBlockTaskFactory.create(eth2Network, targetChain, blockRoot);
    if (allTasks.putIfAbsent(blockRoot, task) != null) {
      // We're already tracking this task
      task.cancel();
      return;
    }
    LOG.trace("Queue block to be fetched: {}", blockRoot);
    queueTask(task);
  }

  @Override
  public void notifyBlockReceived(final SignedBeaconBlock block) {
    cancelRecentBlockRequest(block.getParentRoot());
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
    allTasks.remove(task.getBlockRoot(), task);
  }

  private void queueTask(FetchBlockTask task) {
    pendingTasks.add(task);
    checkTasks();
  }

  private void queueTaskWithDelay(FetchBlockTask task, Duration delay) {
    asyncRunner
        .getDelayedFuture(delay.getSeconds(), TimeUnit.SECONDS)
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

  private void handleFetchedBlock(FetchBlockTask task, final SignedBeaconBlock block) {
    LOG.trace("Successfully fetched block: {}", block);
    // Follow the chain. fetchAncestors will handle notifications if the new block is importable
    fetchAncestors(block, task.getTargetChain());
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
    FetchBlockTask create(
        P2PNetwork<Eth2Peer> eth2Network, SlotAndBlockRoot targetChain, Bytes32 blockRoot);
  }

  public interface BlockSubscriber {
    void onBlock(SignedBeaconBlock block);
  }
}
