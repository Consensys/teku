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

package tech.pegasys.teku.beacon.sync.gossip.executionpayloads;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.FetchExecutionPayloadTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.gossip.AbstractFetchService;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;

public class RecentExecutionPayloadsFetchService
    extends AbstractFetchService<
        Bytes32, FetchExecutionPayloadTask, Optional<SignedExecutionPayloadEnvelope>>
    implements RecentExecutionPayloadsFetcher {

  private static final Logger LOG = LogManager.getLogger();

  public static final int MAX_CONCURRENT_REQUESTS = 3;

  private final ForwardSync forwardSync;
  private final ExecutionPayloadManager executionPayloadManager;
  private final FetchTaskFactory fetchTaskFactory;
  private final Subscribers<ExecutionPayloadSubscriber> executionPayloadSubscribers =
      Subscribers.create(true);

  RecentExecutionPayloadsFetchService(
      final AsyncRunner asyncRunner,
      final ForwardSync forwardSync,
      final ExecutionPayloadManager executionPayloadManager,
      final FetchTaskFactory fetchTaskFactory,
      final int maxConcurrentRequests) {
    super(asyncRunner, maxConcurrentRequests);
    this.forwardSync = forwardSync;
    this.executionPayloadManager = executionPayloadManager;
    this.fetchTaskFactory = fetchTaskFactory;
  }

  public static RecentExecutionPayloadsFetchService create(
      final AsyncRunner asyncRunner,
      final ForwardSync forwardSync,
      final ExecutionPayloadManager executionPayloadManager,
      final FetchTaskFactory fetchTaskFactory) {
    return new RecentExecutionPayloadsFetchService(
        asyncRunner,
        forwardSync,
        executionPayloadManager,
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
  public void subscribeExecutionPayloadFetched(final ExecutionPayloadSubscriber subscriber) {
    executionPayloadSubscribers.subscribe(subscriber);
  }

  @Override
  public void requestRecentExecutionPayload(final Bytes32 blockRoot) {
    if (forwardSync.isSyncActive()) {
      // Forward sync already in progress, assume it will fetch any missing execution payloads
      return;
    }
    final FetchExecutionPayloadTask task = createTask(blockRoot);
    if (allTasks.putIfAbsent(blockRoot, task) != null) {
      // We're already tracking this task
      task.cancel();
      return;
    }
    LOG.trace("Queue execution payload to be fetched: {}", blockRoot);
    queueTask(task);
  }

  @Override
  public void cancelRecentExecutionPayloadRequest(final Bytes32 blockRoot) {
    cancelRequest(blockRoot);
  }

  @Override
  public FetchExecutionPayloadTask createTask(final Bytes32 key) {
    return fetchTaskFactory.createFetchExecutionPayloadTask(key);
  }

  @Override
  public void processFetchedResult(
      final FetchExecutionPayloadTask task,
      final Optional<SignedExecutionPayloadEnvelope> executionPayload) {
    executionPayload.ifPresent(
        payload -> executionPayloadSubscribers.forEach(s -> s.onExecutionPayload(payload)));
    // After retrieved execution payload has been processed, stop tracking it
    removeTask(task);
  }

  @Override
  public void onExecutionPayloadImported(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    cancelRecentExecutionPayloadRequest(executionPayload.getMessage().getBeaconBlockRoot());
  }

  private void setupSubscribers() {
    executionPayloadManager.subscribeRequiredExecutionPayload(this::requestRecentExecutionPayload);
  }
}
