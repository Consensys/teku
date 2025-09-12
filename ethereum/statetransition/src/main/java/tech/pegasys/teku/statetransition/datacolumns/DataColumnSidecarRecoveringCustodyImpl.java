/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public class DataColumnSidecarRecoveringCustodyImpl implements DataColumnSidecarRecoveringCustody {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarByRootCustody delegate;
  private final AsyncRunner asyncRunner;
  private final MiscHelpersFulu miscHelpers;
  private final KZG kzg;
  private final Spec spec;
  private final Consumer<DataColumnSidecar> dataColumnSidecarPublisher;
  private final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier;

  private final long columnCount;
  private final int recoverColumnCount;
  private final int groupCount;
  private final AtomicBoolean isSuperNode = new AtomicBoolean();

  final Function<UInt64, Duration> slotToRecoveryDelay;
  private final Map<SlotAndBlockRoot, RecoveryTask> recoveryTasks;

  private final Subscribers<DataColumnSidecarManager.ValidDataColumnSidecarsListener>
      validDataColumnSidecarsSubscribers = Subscribers.create(true);

  private final Counter totalDataAvailabilityReconstructedColumns;
  private final MetricsHistogram dataAvailabilityReconstructionTimeSeconds;

  public DataColumnSidecarRecoveringCustodyImpl(
      final DataColumnSidecarByRootCustody delegate,
      final AsyncRunner asyncRunner,
      final Spec spec,
      final MiscHelpersFulu miscHelpers,
      final KZG kzg,
      final Consumer<DataColumnSidecar> dataColumnSidecarPublisher,
      final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier,
      final int columnCount,
      final int groupCount,
      final Function<UInt64, Duration> slotToRecoveryDelay,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.miscHelpers = miscHelpers;
    this.kzg = kzg;
    this.spec = spec;
    this.dataColumnSidecarPublisher = dataColumnSidecarPublisher;
    this.custodyGroupCountManagerSupplier = custodyGroupCountManagerSupplier;
    this.recoveryTasks =
        LimitedMap.createSynchronizedNatural(spec.getGenesisSpec().getSlotsPerEpoch());
    this.slotToRecoveryDelay = slotToRecoveryDelay;
    this.columnCount = columnCount;
    this.groupCount = groupCount;
    this.recoverColumnCount = columnCount / 2;
    this.totalDataAvailabilityReconstructedColumns =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "data_availability_reconstructed_columns_total",
            "Total count of reconstructed columns");
    this.dataAvailabilityReconstructionTimeSeconds =
        new MetricsHistogram(
            metricsSystem,
            timeProvider,
            TekuMetricCategory.BEACON,
            "data_availability_reconstruction_time_seconds",
            "Time taken to reconstruct columns",
            new double[] {
              0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 5.0,
              7.5, 10.0
            });
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (shouldSkipProcessing(slot)) {
      return;
    }
    asyncRunner
        .runAfterDelay(
            () -> {
              LOG.debug("Check if recovery needed for slot: {}", slot);

              recoveryTasks.keySet().stream()
                  .filter(key -> key.getSlot().isLessThanOrEqualTo(slot))
                  .map(recoveryTasks::get)
                  .forEach(
                      recoveryTask -> {
                        if (recoveryTask.timedOut().compareAndSet(false, true)) {
                          maybeStartRecovery(recoveryTask);
                        }
                      });
            },
            slotToRecoveryDelay.apply(slot))
        .finishWarn(LOG);
  }

  private boolean shouldSkipProcessing(final UInt64 slot) {
    if (isActiveSuperNode(slot)) {
      return false;
    }
    if (custodyGroupCountManagerSupplier.get().getCustodyGroupCount() == groupCount) {
      if (!isSuperNode.get()) {
        LOG.debug(
            "Number of required custody groups reached maximum. Activating super node reconstruction.");
        isSuperNode.set(true);
      }
      return false;
    }
    return true;
  }

  protected void maybeStartRecovery(final RecoveryTask task) {
    if (readyToBeRecovered(task)) {
      if (task.recoveryStarted.compareAndSet(false, true)) {
        if (task.existingSidecars.size() == columnCount) {
          task.existingSidecars.clear();
          return;
        }
        scheduleRecoveryTask(task);
      }
    }
  }

  @VisibleForTesting
  protected void scheduleRecoveryTask(final RecoveryTask task) {
    asyncRunner
        .runAsync(() -> prepareAndInitiateRecovery(task))
        .whenException(
            ex -> {
              LOG.debug(
                  "Error during recovery of {} task with {} sidecars",
                  task.slotAndBlockRoot,
                  task.existingSidecars.size(),
                  ex);
              // release task for future retries only if error happened during recovery
              task.recoveryStarted.set(false);
            })
        .finishError(LOG);
  }

  private boolean readyToBeRecovered(final RecoveryTask task) {
    if (task.recoveryStarted().get()) {
      // already started
      return false;
    }

    if (!task.timedOut().get()) {
      return false;
    }

    if (task.existingSidecars().size() < recoverColumnCount) {
      // not enough columns collected
      return false;
    }

    return true;
  }

  @Override
  public void subscribeToValidDataColumnSidecars(
      final DataColumnSidecarManager.ValidDataColumnSidecarsListener sidecarsListener) {
    validDataColumnSidecarsSubscribers.subscribe(sidecarsListener);
  }

  private boolean isActiveSuperNode(final UInt64 slot) {
    return isSuperNode.get()
        && spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU);
  }

  protected record RecoveryTask(
      SlotAndBlockRoot slotAndBlockRoot,
      Map<DataColumnSlotAndIdentifier, DataColumnSidecar> existingSidecars,
      AtomicBoolean recoveryStarted,
      AtomicBoolean timedOut) {}

  private void prepareAndInitiateRecovery(final RecoveryTask task) {
    LOG.debug(
        "Recovery for block: {}. DataColumnSidecars found: {}",
        task.slotAndBlockRoot,
        task.existingSidecars.size());

    try (final MetricsHistogram.Timer timer =
        dataAvailabilityReconstructionTimeSeconds.startTimer()) {
      initiateRecovery(task, task.existingSidecars.values(), timer);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void initiateRecovery(
      final RecoveryTask recoveryTask,
      final Collection<DataColumnSidecar> sidecars,
      final MetricsHistogram.Timer timer) {
    final List<DataColumnSidecar> recoveredSidecars =
        miscHelpers.reconstructAllDataColumnSidecars(sidecars, kzg);
    timer.closeUnchecked().run();

    final Set<UInt64> existingSidecarsIndices =
        sidecars.stream().map(DataColumnSidecar::getIndex).collect(Collectors.toUnmodifiableSet());
    totalDataAvailabilityReconstructedColumns.inc(recoveredSidecars.size() - sidecars.size());
    recoveredSidecars.stream()
        .filter(sidecar -> !existingSidecarsIndices.contains(sidecar.getIndex()))
        .forEach(
            dataColumnSidecar -> {
              validDataColumnSidecarsSubscribers.forEach(
                  l -> l.onNewValidSidecar(dataColumnSidecar, RemoteOrigin.RECOVERED));
              delegate
                  .onNewValidatedDataColumnSidecar(dataColumnSidecar, RemoteOrigin.RECOVERED)
                  .finishError(LOG);
              dataColumnSidecarPublisher.accept(dataColumnSidecar);
            });
    recoveryTask.existingSidecars.clear();
    LOG.debug(
        "Data column sidecars recovery finished for block: {}", recoveryTask.slotAndBlockRoot);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecarByRoot(
      final DataColumnIdentifier columnId) {
    return delegate.getCustodyDataColumnSidecarByRoot(columnId);
  }

  @Override
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    // Recovery is not needed for locally produced or recovered data,
    // we will get everything for it in custody w/o reconstruction
    if (remoteOrigin.equals(RemoteOrigin.RPC) || remoteOrigin.equals(RemoteOrigin.GOSSIP)) {
      createOrUpdateRecoveryTaskForDataColumnSidecar(dataColumnSidecar);
    }
    return delegate.onNewValidatedDataColumnSidecar(dataColumnSidecar, remoteOrigin);
  }

  private void createOrUpdateRecoveryTaskForDataColumnSidecar(final DataColumnSidecar sidecar) {
    final RecoveryTask task =
        recoveryTasks.computeIfAbsent(
            sidecar.getSlotAndBlockRoot(),
            __ ->
                new RecoveryTask(
                    sidecar.getSlotAndBlockRoot(),
                    new ConcurrentHashMap<>(),
                    new AtomicBoolean(false),
                    new AtomicBoolean(false)));
    task.existingSidecars().put(DataColumnSlotAndIdentifier.fromDataColumn(sidecar), sidecar);
    maybeStartRecovery(task);
  }

  @Override
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
    return delegate.retrieveMissingColumns();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return delegate.getCustodyDataColumnSidecar(columnId);
  }

  @Override
  public SafeFuture<Boolean> hasCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return delegate.hasCustodyDataColumnSidecar(columnId);
  }
}
