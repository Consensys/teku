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

import com.google.common.base.MoreObjects;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
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
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
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
  private final CustodyGroupCountManager custodyGroupCountManager;

  private final long columnCount;
  private final int recoverColumnCount;
  private final int groupCount;
  private final AtomicBoolean isSuperNode;

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
      final CustodyGroupCountManager custodyGroupCountManager,
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
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.recoveryTasks =
        LimitedMap.createSynchronizedNatural(spec.getGenesisSpec().getSlotsPerEpoch());
    this.isSuperNode =
        new AtomicBoolean(custodyGroupCountManager.getCustodyGroupCount() == groupCount);
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
    if (!isActiveSuperNode(slot)) {
      if (custodyGroupCountManager.getCustodyGroupSyncedCount() == groupCount) {
        LOG.debug(
            "Number of required custody groups reached maximum custody groups. Activating super node reconstruction.");
        isSuperNode.set(true);
      } else {
        return;
      }
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
        .ifExceptionGetsHereRaiseABug();
  }

  @Override
  public void onNewBlock(final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    if (!isActiveSuperNode(block.getSlot())) {
      return;
    }

    if (remoteOrigin.isPresent()
        && (remoteOrigin.get().equals(RemoteOrigin.LOCAL_EL)
            || remoteOrigin.get().equals(RemoteOrigin.LOCAL_PROPOSAL))) {
      // skip locally produced blocks, we will get everything for it in custody w/o reconstruction
      return;
    }
    createOrUpdateRecoveryTaskForBlock(block.getMessage());
  }

  private synchronized void createOrUpdateRecoveryTaskForBlock(final BeaconBlock block) {
    if (recoveryTasks.containsKey(block.getSlotAndBlockRoot())) {
      final RecoveryTask existing = recoveryTasks.get(block.getSlotAndBlockRoot());
      if (existing.block().get() == null) {
        existing.block().set(block);
        maybeStartRecovery(existing);
      }
    } else {
      recoveryTasks.put(
          block.getSlotAndBlockRoot(),
          new RecoveryTask(
              new AtomicReference<>(block),
              new HashSet<>(),
              new AtomicBoolean(false),
              new AtomicBoolean(false)));
    }
  }

  private synchronized void maybeStartRecovery(final RecoveryTask task) {
    if (readyToBeRecovered(task)) {
      task.recoveryStarted().set(true);
      if (task.existingColumnIds().size() != columnCount) {
        asyncRunner
            .runAsync(() -> prepareAndInitiateRecovery(task))
            .finish(
                error -> {
                  LOG.error("DataColumnSidecars recovery task {} failed", task, error);
                });
      }
    }
  }

  private boolean readyToBeRecovered(final RecoveryTask task) {
    if (task.recoveryStarted().get()) {
      // already started
      return false;
    }

    if (!task.timedOut().get()) {
      return false;
    }

    if (task.existingColumnIds().size() < recoverColumnCount) {
      // not enough columns collected
      return false;
    }

    if (task.block().get() == null) {
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

  private record RecoveryTask(
      AtomicReference<BeaconBlock> block,
      Set<DataColumnSlotAndIdentifier> existingColumnIds,
      AtomicBoolean recoveryStarted,
      AtomicBoolean timedOut) {

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("block", block.get() == null ? "null" : block.get().toLogString())
          .add(
              "existingColumnIds",
              existingColumnIds.isEmpty()
                  ? "empty"
                  : existingColumnIds.stream().findFirst()
                      + ", "
                      + existingColumnIds.size()
                      + " total")
          .add("recoveryStarted", recoveryStarted)
          .add("timedOut", timedOut)
          .toString();
    }
  }

  private void prepareAndInitiateRecovery(final RecoveryTask task) {
    final SafeFuture<List<DataColumnSidecar>> list =
        AsyncStream.create(task.existingColumnIds().stream())
            .mapAsync(delegate::getCustodyDataColumnSidecar)
            .map(Optional::get)
            .toList();
    initiateRecovery(task.block().get(), list);
  }

  private void initiateRecovery(
      final BeaconBlock block, final SafeFuture<List<DataColumnSidecar>> list) {
    LOG.debug("Starting data columns sidecars recovery for block: {}", block.getSlotAndBlockRoot());

    final MetricsHistogram.Timer timer = dataAvailabilityReconstructionTimeSeconds.startTimer();

    list.thenAccept(
            sidecars -> {
              LOG.debug(
                  "Recovery for block: {}. DatacolumnSidecars found: {}",
                  block.getSlotAndBlockRoot(),
                  sidecars.size());
              final List<DataColumnSidecar> recoveredSidecars =
                  miscHelpers.reconstructAllDataColumnSidecars(sidecars, kzg);
              timer.closeUnchecked();

              final Set<UInt64> existingSidecarsIndices =
                  sidecars.stream()
                      .map(DataColumnSidecar::getIndex)
                      .collect(Collectors.toUnmodifiableSet());
              totalDataAvailabilityReconstructedColumns.inc(
                  recoveredSidecars.size() - sidecars.size());
              recoveredSidecars.stream()
                  .filter(sidecar -> !existingSidecarsIndices.contains(sidecar.getIndex()))
                  .forEach(
                      dataColumnSidecar -> {
                        validDataColumnSidecarsSubscribers.forEach(
                            l -> l.onNewValidSidecar(dataColumnSidecar, RemoteOrigin.RECOVERED));
                        delegate
                            .onNewValidatedDataColumnSidecar(dataColumnSidecar)
                            .ifExceptionGetsHereRaiseABug();
                        dataColumnSidecarPublisher.accept(dataColumnSidecar);
                      });
              LOG.debug(
                  "Data column sidecars recovery finished for block: {}",
                  block.getSlotAndBlockRoot());
            })
        .alwaysRun(timer.closeUnchecked())
        .ifExceptionGetsHereRaiseABug();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecarByRoot(
      final DataColumnIdentifier columnId) {
    return delegate.getCustodyDataColumnSidecarByRoot(columnId);
  }

  @Override
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar) {
    createOrUpdateRecoveryTaskForDataColumnSidecar(
        DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar));
    return delegate.onNewValidatedDataColumnSidecar(dataColumnSidecar);
  }

  private synchronized void createOrUpdateRecoveryTaskForDataColumnSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    if (recoveryTasks.containsKey(identifier.getSlotAndBlockRoot())) {
      final RecoveryTask existing = recoveryTasks.get(identifier.getSlotAndBlockRoot());
      existing.existingColumnIds().add(identifier);
      maybeStartRecovery(existing);
    } else {
      RecoveryTask recoveryTask =
          new RecoveryTask(
              new AtomicReference<>(null),
              new HashSet<>(List.of(identifier)),
              new AtomicBoolean(false),
              new AtomicBoolean(false));
      recoveryTasks.put(identifier.getSlotAndBlockRoot(), recoveryTask);
    }
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
