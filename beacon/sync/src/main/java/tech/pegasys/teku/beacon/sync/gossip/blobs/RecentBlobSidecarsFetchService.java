/*
 * Copyright Consensys Software Inc., 2022
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

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.BlockRootAndBlobIdentifiers;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlobSidecarsTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.gossip.AbstractFetchService;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;

public class RecentBlobSidecarsFetchService
    extends AbstractFetchService<
        BlockRootAndBlobIdentifiers, FetchBlobSidecarsTask, List<BlobSidecar>>
    implements RecentBlobSidecarsFetcher {

  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_CONCURRENT_REQUESTS = 3;

  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final ForwardSync forwardSync;
  private final FetchTaskFactory fetchTaskFactory;

  private final Subscribers<BlobSidecarSubscriber> blobSidecarSubscribers =
      Subscribers.create(true);

  RecentBlobSidecarsFetchService(
      final AsyncRunner asyncRunner,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory,
      final int maxConcurrentRequests) {
    super(asyncRunner, maxConcurrentRequests);
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.forwardSync = forwardSync;
    this.fetchTaskFactory = fetchTaskFactory;
  }

  public static RecentBlobSidecarsFetchService create(
      final AsyncRunner asyncRunner,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory) {
    return new RecentBlobSidecarsFetchService(
        asyncRunner,
        blockBlobSidecarsTrackersPool,
        forwardSync,
        fetchTaskFactory,
        MAX_CONCURRENT_REQUESTS);
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
  public void subscribeBlobSidecarFetched(final BlobSidecarSubscriber subscriber) {
    blobSidecarSubscribers.subscribe(subscriber);
  }

  @Override
  public void requestRecentBlobSidecars(
      final Bytes32 blockRoot, final List<BlobIdentifier> blobIdentifiers) {
    if (forwardSync.isSyncActive()) {
      // Forward sync already in progress, assume it will fetch any missing blob sidecars
      return;
    }
    final List<BlobIdentifier> requiredBlobIdentifiers =
        blobIdentifiers.stream()
            .filter(
                blobIdentifier ->
                    !blockBlobSidecarsTrackersPool.containsBlobSidecar(blobIdentifier))
            .toList();
    if (requiredBlobIdentifiers.isEmpty()) {
      // We already have all required blob sidecars
      return;
    }
    final BlockRootAndBlobIdentifiers key =
        new BlockRootAndBlobIdentifiers(blockRoot, requiredBlobIdentifiers);
    final FetchBlobSidecarsTask task = createTask(key);
    if (allTasks.putIfAbsent(key, task) != null) {
      // We're already tracking this task
      task.cancel();
      return;
    }
    LOG.trace("Queue blob sidecars to be fetched: {}", requiredBlobIdentifiers);
    queueTask(task);
  }

  @Override
  public void cancelRecentBlobSidecarsRequests(final Bytes32 blockRoot) {
    allTasks.forEach(
        (key, __) -> {
          if (key.blockRoot().equals(blockRoot)) {
            cancelRequest(key);
          }
        });
  }

  @Override
  public FetchBlobSidecarsTask createTask(final BlockRootAndBlobIdentifiers key) {
    return fetchTaskFactory.createFetchBlobSidecarsTask(key);
  }

  @Override
  public void processFetchedResult(
      final FetchBlobSidecarsTask task, final List<BlobSidecar> result) {
    result.forEach(
        blobSidecar -> {
          LOG.trace("Successfully fetched blob sidecar: {}", blobSidecar);
          blobSidecarSubscribers.forEach(s -> s.onBlobSidecar(blobSidecar));
        });
    // After retrieved blob sidecars have been processed, stop tracking it
    removeTask(task);
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {}

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    cancelRecentBlobSidecarsRequests(block.getRoot());
  }

  private void setupSubscribers() {
    blockBlobSidecarsTrackersPool.subscribeRequiredBlobSidecars(this::requestRecentBlobSidecars);
    blockBlobSidecarsTrackersPool.subscribeRequiredBlobSidecarsDropped(
        this::cancelRecentBlobSidecarsRequests);
    forwardSync.subscribeToSyncChanges(this::onSyncStatusChanged);
  }

  private void onSyncStatusChanged(final boolean syncActive) {
    if (syncActive) {
      return;
    }
    // Ensure we are requesting the blob sidecars not already filled in by the sync
    // We may have ignored these requested blob sidecars while the sync was in progress
    blockBlobSidecarsTrackersPool
        .getAllRequiredBlobSidecars()
        .forEach(this::requestRecentBlobSidecars);
  }
}
