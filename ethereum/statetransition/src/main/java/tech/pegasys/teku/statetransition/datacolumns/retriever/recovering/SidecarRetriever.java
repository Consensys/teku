/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.datacolumns.retriever.recovering;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolver;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;

@SuppressWarnings("unused")
public class SidecarRetriever implements DataColumnSidecarRetriever {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarRetriever downloader;
  private final KZG kzg;
  private final MiscHelpersFulu miscHelpersFulu;
  private final CanonicalBlockResolver blockResolver;
  private final DataColumnSidecarDbAccessor sidecarDB;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final Duration recoveryTimeout;
  private final Duration recoveryCheckInterval;
  private final int numberOfColumns;
  private final int numberOfColumnsRequiredToReconstruct;
  private Cancellable pendingRequestsChecker;

  private final Map<DataColumnSlotAndIdentifier, PendingRecoveryRequest> requests =
      new ConcurrentSkipListMap<>();
  private final Map<Bytes32, RebuildColumnsTask> rebuildTasks = new ConcurrentSkipListMap<>();

  public SidecarRetriever(
      final DataColumnSidecarRetriever delegate,
      final KZG kzg,
      final MiscHelpersFulu miscHelpersFulu,
      final CanonicalBlockResolver blockResolver,
      final DataColumnSidecarDbAccessor sidecarDB,
      final AsyncRunner asyncRunner,
      final Duration recoveryTimeout,
      final Duration recoveryCheckInterval,
      final TimeProvider timeProvider,
      final int numberOfColumns) {
    downloader = delegate;
    this.kzg = kzg;
    this.miscHelpersFulu = miscHelpersFulu;
    this.blockResolver = blockResolver;
    this.sidecarDB = sidecarDB;
    this.asyncRunner = asyncRunner;
    this.recoveryTimeout = recoveryTimeout;
    this.recoveryCheckInterval = recoveryCheckInterval;
    this.timeProvider = timeProvider;
    this.numberOfColumns = numberOfColumns;
    // reconstruction of all columns is possible with >= 50% of the column data
    this.numberOfColumnsRequiredToReconstruct = Math.ceilDiv(numberOfColumns, 2);
  }

  @VisibleForTesting
  int pendingRequestCount() {
    return requests.size();
  }

  @Override
  public synchronized void start() {
    if (pendingRequestsChecker != null) {
      return;
    }
    pendingRequestsChecker =
        asyncRunner.runWithFixedDelay(
            this::checkPendingRequests,
            recoveryCheckInterval,
            recoveryCheckInterval,
            error ->
                LOG.error(
                    "Failed to check if {} pending data column sidecars requests require recovery to be run",
                    requests.size(),
                    error));
  }

  @Override
  public synchronized void stop() {
    if (pendingRequestsChecker != null) {
      pendingRequestsChecker.cancel();
      pendingRequestsChecker = null;
    }

    rebuildTasks.values().forEach(RebuildColumnsTask::cancel);
    rebuildTasks.clear();

    requests.values().forEach(PendingRecoveryRequest::cancel);
    requests.clear();
  }

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier columnId) {
    final UInt64 columnIndex = columnId.columnIndex();
    final PendingRecoveryRequest pendingRecoveryRequest =
        requests.computeIfAbsent(
            columnId,
            __ -> {
              final PendingRecoveryRequest request =
                  new PendingRecoveryRequest(
                      columnId,
                      downloader.retrieve(columnId),
                      timeProvider.getTimeInMillis(),
                      recoveryTimeout.dividedBy(2),
                      recoveryTimeout);
              request.getFuture().always(() -> requests.remove(columnId));
              return request;
            });
    return pendingRecoveryRequest.getFuture();
  }

  @Override
  public void flush() {
    downloader.flush();
  }

  @Override
  public void onNewValidatedSidecar(final DataColumnSidecar sidecar) {
    downloader.onNewValidatedSidecar(sidecar);
  }

  @VisibleForTesting
  Cancellable getPendingRequestsChecker() {
    return pendingRequestsChecker;
  }

  @VisibleForTesting
  Map<DataColumnSlotAndIdentifier, PendingRecoveryRequest> getPendingRequests() {
    return requests;
  }

  private void checkPendingRequests() {
    if (requests.isEmpty() && rebuildTasks.isEmpty()) {
      return;
    }
    final UInt64 currentTime = timeProvider.getTimeInMillis();
    LOG.trace(
        "Checking pending requests: {} requests, {} rebuild tasks",
        requests.size(),
        rebuildTasks.size());

    // make sure requests are within their timeout
    requests.values().forEach(request -> request.checkTimeout(currentTime));

    // update state of any requests that are active
    // TODO timeout of half the recovery period hard coded?
    requests.values().stream()
        .filter(PendingRecoveryRequest::isFailedDownloading)
        .forEach(
            request -> {
              final RebuildColumnsTask rebuildColumnsTask =
                  rebuildTasks.computeIfAbsent(
                      request.getBlockRoot(),
                      __ ->
                          new RebuildColumnsTask(
                              request.getSlotAndBlockRoot(),
                              currentTime,
                              recoveryTimeout.dividedBy(2),
                              numberOfColumnsRequiredToReconstruct,
                              sidecarDB,
                              miscHelpersFulu,
                              kzg));
              rebuildColumnsTask.addTask(request);
            });
    rebuildTasks.entrySet().removeIf(entry -> entry.getValue().isDone(currentTime));

    rebuildTasks.forEach((key, value) -> value.checkQueryResult());

    LOG.trace("after cleanup: {} requests, {} rebuild tasks", requests.size(), rebuildTasks.size());
  }
}
