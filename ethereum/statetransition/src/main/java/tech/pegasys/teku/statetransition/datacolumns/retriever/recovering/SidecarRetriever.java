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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;

public class SidecarRetriever implements DataColumnSidecarRetriever {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarRetriever delegate;
  private final MiscHelpersFulu miscHelpersFulu;
  private final DataColumnSidecarDbAccessor sidecarDB;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final Duration recoveryTimeout;
  private final Duration downloadTimeout;
  private final Duration recoveryCheckInterval;
  private final int numberOfColumnsRequiredToReconstruct;
  private Cancellable pendingRequestsChecker;
  private final AtomicReference<CustodyGroupCountManager> custodyGroupCountManagerRef;

  private final Map<DataColumnSlotAndIdentifier, PendingRecoveryRequest> requests =
      new ConcurrentHashMap<>();
  private final Map<Bytes32, RebuildColumnsTask> rebuildTasks = new ConcurrentHashMap<>();

  private final LabelledMetric<Counter> sidecarRecoveryMetric;

  static final String DOWNLOADED = "DOWNLOADED";
  static final String RECOVERED = "RECOVERED";
  static final String CANCELLED = "CANCELLED";
  static final String RECOVERY_METRIC_NAME = "data_column_sidecar_recovery_requests";

  public SidecarRetriever(
      final DataColumnSidecarRetriever delegate,
      final MiscHelpersFulu miscHelpersFulu,
      final DataColumnSidecarDbAccessor sidecarDB,
      final AsyncRunner asyncRunner,
      final Duration recoveryTimeout,
      final Duration downloadTimeout,
      final Duration recoveryCheckInterval,
      final TimeProvider timeProvider,
      final int numberOfColumns,
      final AtomicReference<CustodyGroupCountManager> custodyGroupCountManagerRef,
      final MetricsSystem metricsSystem) {
    this.delegate = delegate;
    this.miscHelpersFulu = miscHelpersFulu;
    this.sidecarDB = sidecarDB;
    this.asyncRunner = asyncRunner;
    this.recoveryTimeout = recoveryTimeout;
    this.downloadTimeout = downloadTimeout;
    this.recoveryCheckInterval = recoveryCheckInterval;
    this.timeProvider = timeProvider;
    this.custodyGroupCountManagerRef = custodyGroupCountManagerRef;

    LOG.debug(
        "Download timeout {} ms, recovery timeout {} seconds}",
        downloadTimeout.toMillis(),
        recoveryTimeout.toSeconds());
    // reconstruction of all columns is possible with >= 50% of the column data
    this.numberOfColumnsRequiredToReconstruct = Math.ceilDiv(numberOfColumns, 2);
    sidecarRecoveryMetric =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            RECOVERY_METRIC_NAME,
            "The number of sidecar recovery requests performed",
            "result");
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
  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier columnId) {
    final PendingRecoveryRequest pendingRecoveryRequest =
        requests.computeIfAbsent(
            columnId,
            __ ->
                new PendingRecoveryRequest(
                    columnId,
                    delegate.retrieve(columnId),
                    timeProvider.getTimeInMillis(),
                    recoveryTimeout,
                    downloadTimeout,
                    sidecarRecoveryMetric,
                    () -> requests.remove(columnId)));
    return pendingRecoveryRequest.getFuture();
  }

  @Override
  public void flush() {
    delegate.flush();
  }

  @Override
  public void onNewValidatedSidecar(final DataColumnSidecar sidecar) {
    LOG.trace(
        "new validated sidecar ({}), index {}", sidecar.getSlotAndBlockRoot(), sidecar.getIndex());
    delegate.onNewValidatedSidecar(sidecar);
  }

  @VisibleForTesting
  Cancellable getPendingRequestsChecker() {
    return pendingRequestsChecker;
  }

  @VisibleForTesting
  Map<DataColumnSlotAndIdentifier, PendingRecoveryRequest> getPendingRequests() {
    return requests;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void checkPendingRequests() {
    if (requests.isEmpty() && rebuildTasks.isEmpty()) {
      return;
    }
    final UInt64 currentTime = timeProvider.getTimeInMillis();
    LOG.debug(
        "Checking pending requests ({}): {} requests, {} rebuild tasks",
        currentTime,
        requests.size(),
        rebuildTasks.size());

    // make sure requests are within their timeout
    requests.values().forEach(request -> request.checkTimeout(currentTime));

    // update state of any requests that are active
    requests.values().stream()
        .filter(PendingRecoveryRequest::isFailedDownloading)
        .forEach(
            request -> {
              if (custodyGroupCountManagerRef.get().getCustodyGroupCount() == 128) {
                final RebuildColumnsTask rebuildColumnsTask =
                    rebuildTasks.computeIfAbsent(
                        request.getBlockRoot(),
                        __ -> {
                          LOG.debug(
                              "Rebuilding columns for slot {} root {}",
                              request.getSlot(),
                              request.getBlockRoot());
                          return new RebuildColumnsTask(
                              request.getSlotAndBlockRoot(),
                              currentTime,
                              recoveryTimeout.minus(downloadTimeout),
                              numberOfColumnsRequiredToReconstruct,
                              sidecarDB,
                              miscHelpersFulu);
                        });
                rebuildColumnsTask.addTask(request);
              } else {
                LOG.debug(
                    "Custody group count {} is not viable to reconstruct for slot {} root {}",
                    custodyGroupCountManagerRef.get().getCustodyGroupCount(),
                    request.getSlot(),
                    request.getBlockRoot());
                request.cancel();
              }
            });
    rebuildTasks.entrySet().removeIf(entry -> entry.getValue().isDone(currentTime));

    rebuildTasks.forEach((key, value) -> value.checkQueryResult());
  }
}
