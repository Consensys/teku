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

package tech.pegasys.teku.beacon.sync.gossip.blocks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlockTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.gossip.AbstractFetchService;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.util.PendingPool;

public class FetchRecentBlocksService
    extends AbstractFetchService<Bytes32, FetchBlockTask, SignedBeaconBlock>
    implements RecentBlockFetcherService {

  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_CONCURRENT_REQUESTS = 3;

  private final ForwardSync forwardSync;
  private final PendingPool<SignedBeaconBlock> pendingBlocksPool;
  private final FetchTaskFactory fetchTaskFactory;
  private final Subscribers<BlockSubscriber> blockSubscribers = Subscribers.create(true);

  FetchRecentBlocksService(
      final AsyncRunner asyncRunner,
      final PendingPool<SignedBeaconBlock> pendingBlocksPool,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory,
      final int maxConcurrentRequests) {
    super(asyncRunner, maxConcurrentRequests);
    this.forwardSync = forwardSync;
    this.pendingBlocksPool = pendingBlocksPool;
    this.fetchTaskFactory = fetchTaskFactory;
  }

  public static FetchRecentBlocksService create(
      final AsyncRunner asyncRunner,
      final PendingPool<SignedBeaconBlock> pendingBlocksPool,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory) {
    return new FetchRecentBlocksService(
        asyncRunner, pendingBlocksPool, forwardSync, fetchTaskFactory, MAX_CONCURRENT_REQUESTS);
  }

  @Override
  protected SafeFuture<?> doStart() {
    setupSubscribers();
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public void subscribeBlockFetched(final BlockSubscriber subscriber) {
    blockSubscribers.subscribe(subscriber);
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
    final FetchBlockTask task = createTask(blockRoot);
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
    cancelRequest(blockRoot);
  }

  @Override
  public FetchBlockTask createTask(final Bytes32 key) {
    return fetchTaskFactory.createFetchBlockTask(key);
  }

  @Override
  public void processFetchedResult(final FetchBlockTask task, final SignedBeaconBlock block) {
    LOG.trace("Successfully fetched block: {}", block);
    blockSubscribers.forEach(s -> s.onBlock(block));
    // After retrieved block has been processed, stop tracking it
    removeTask(task);
  }

  private void setupSubscribers() {
    pendingBlocksPool.subscribeRequiredBlockRoot(this::requestRecentBlock);
    pendingBlocksPool.subscribeRequiredBlockRootDropped(this::cancelRecentBlockRequest);
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
}
