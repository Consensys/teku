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

package tech.pegasys.teku.beacon.sync.gossip.blobs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlobSidecarTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.gossip.AbstractFetchService;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class FetchRecentBlobSidecarsService
    extends AbstractFetchService<BlobIdentifier, FetchBlobSidecarTask, BlobSidecar>
    implements RecentBlobSidecarFetcher {

  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_CONCURRENT_REQUESTS = 3;

  private final ForwardSync forwardSync;
  private final FetchTaskFactory fetchTaskFactory;

  private final Subscribers<BlobSidecarSubscriber> blobSidecarSubscribers =
      Subscribers.create(true);

  FetchRecentBlobSidecarsService(
      final AsyncRunner asyncRunner,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory,
      final int maxConcurrentRequests) {
    super(asyncRunner, maxConcurrentRequests);
    this.forwardSync = forwardSync;
    this.fetchTaskFactory = fetchTaskFactory;
  }

  public static FetchRecentBlobSidecarsService create(
      final AsyncRunner asyncRunner,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory) {
    return new FetchRecentBlobSidecarsService(
        asyncRunner, forwardSync, fetchTaskFactory, MAX_CONCURRENT_REQUESTS);
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
  public void requestRecentBlobSidecar(final BlobIdentifier blobIdentifier) {
    if (forwardSync.isSyncActive()) {
      // Forward sync already in progress, assume it will fetch any missing blob sidecars
      return;
    }
    // TODO: add a check that pending does not have this blob sidecar
    final FetchBlobSidecarTask task = createTask(blobIdentifier);
    if (allTasks.putIfAbsent(blobIdentifier, task) != null) {
      // We're already tracking this task
      task.cancel();
      return;
    }
    LOG.trace("Queue blob sidecar to be fetched: {}", blobIdentifier);
    queueTask(task);
  }

  @Override
  public void cancelRecentBlobSidecarRequest(final BlobIdentifier blobIdentifier) {
    cancelRequest(blobIdentifier);
  }

  private void setupSubscribers() {
    forwardSync.subscribeToSyncChanges(this::onSyncStatusChanged);
    // TODO: add subscriptions in similar way to PendingBlocksPool
  }

  private void onSyncStatusChanged(final boolean syncActive) {
    if (syncActive) {
      return;
    }
    // TODO: implement similar to FetchRecentBlocksService
  }

  @Override
  public FetchBlobSidecarTask createTask(final BlobIdentifier key) {
    return fetchTaskFactory.createFetchBlobSidecarTask(key);
  }

  @Override
  public void processFetchedResult(final FetchBlobSidecarTask task, final BlobSidecar result) {
    LOG.trace("Successfully fetched blob sidecar: {}", result);
    blobSidecarSubscribers.forEach(s -> s.onBlobSidecar(result));
    // After retrieved blob sidecar has been processed, stop tracking it
    removeTask(task);
  }
}
