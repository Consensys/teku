/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.server.pruner;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.storage.api.SidecarArchivePrunableChannel;
import tech.pegasys.teku.storage.server.Database;

public class DataColumnSidecarPruner extends Service implements SidecarArchivePrunableChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Database database;
  private final AsyncRunner asyncRunner;
  private final Duration pruneInterval;
  private final int pruneLimit;
  private final TimeProvider timeProvider;
  private final boolean dataColumnSidecarsStorageCountersEnabled;
  private final SettableLabelledGauge pruningTimingsLabelledGauge;
  private final SettableLabelledGauge pruningActiveLabelledGauge;
  private final String pruningMetricsType;

  private final AtomicLong dataColumnSize = new AtomicLong(0);
  private final AtomicLong earliestDataColumnSidecarSlot = new AtomicLong(-1);
  private final AtomicLong lastDataColumnSidecarArchivePrunableSlot = new AtomicLong(-1);
  // Tracks which slot ranges have been submitted to archiveSidecarsProofs this session.
  // The complement within [0, tillSlot] is what still needs archiving. Chunks are always
  // taken from the highest (most recent) unarchived range first, so newly-prunable epochs
  // are archived before the historical backlog. Resets to empty on restart (in-memory only).
  private final TreeRangeSet<Long> archivedSlotRanges = TreeRangeSet.create();
  private Optional<UInt64> genesisTime = Optional.empty();

  private Optional<Cancellable> scheduledPruner = Optional.empty();

  public DataColumnSidecarPruner(
      final Spec spec,
      final Database database,
      final MetricsSystem metricsSystem,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final Duration pruneInterval,
      final int pruneLimit,
      final boolean dataColumnSidecarsStorageCountersEnabled,
      final String pruningMetricsType,
      final SettableLabelledGauge pruningTimingsLabelledGauge,
      final SettableLabelledGauge pruningActiveLabelledGauge) {
    this.spec = spec;
    this.database = database;
    this.asyncRunner = asyncRunner;
    this.pruneInterval = pruneInterval;
    this.pruneLimit = pruneLimit;
    this.timeProvider = timeProvider;
    this.dataColumnSidecarsStorageCountersEnabled = dataColumnSidecarsStorageCountersEnabled;
    this.pruningMetricsType = pruningMetricsType;
    this.pruningTimingsLabelledGauge = pruningTimingsLabelledGauge;
    this.pruningActiveLabelledGauge = pruningActiveLabelledGauge;
    if (dataColumnSidecarsStorageCountersEnabled) {
      LabelledSuppliedMetric labelledGauge =
          metricsSystem.createLabelledSuppliedGauge(
              TekuMetricCategory.STORAGE,
              "data_column_sidecars",
              "Statistics for Data columns stored",
              "type");

      labelledGauge.labels(dataColumnSize::get, "total");
      labelledGauge.labels(earliestDataColumnSidecarSlot::get, "earliest_slot");
    }
  }

  @Override
  protected SafeFuture<?> doStart() {
    scheduledPruner =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::pruneDataColumnSidecars,
                Duration.ZERO,
                pruneInterval,
                error -> LOG.error("Failed to prune old data column sidecars", error)));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    scheduledPruner.ifPresent(Cancellable::cancel);
    return SafeFuture.COMPLETE;
  }

  /**
   * NOTE: the column pruning must not update earliestAvailableDataColumnSlot db variable. That
   * variable is fully maintained by DasCustodyBackfiller
   */
  private void pruneDataColumnSidecars() {

    final Optional<UInt64> genesisTime = getGenesisTime();
    if (genesisTime.isEmpty()) {
      LOG.debug("Not pruning as no genesis time is available.");
      return;
    }

    pruningActiveLabelledGauge.set(1, pruningMetricsType);
    final long start = System.currentTimeMillis();
    final UInt64 minCustodySlot = calculatePruneSlot();
    LOG.debug("Pruning data column sidecars before slot {}", minCustodySlot);
    database.pruneAllSidecars(minCustodySlot.minusMinZero(1), pruneLimit);

    pruningTimingsLabelledGauge.set(System.currentTimeMillis() - start, pruningMetricsType);
    pruningActiveLabelledGauge.set(0, pruningMetricsType);
    if (dataColumnSidecarsStorageCountersEnabled) {
      dataColumnSize.set(database.getSidecarColumnCount());
      earliestDataColumnSidecarSlot.set(
          database.getEarliestDataColumnSidecarSlot().map(UInt64::longValue).orElse(-1L));
    }

    if (lastDataColumnSidecarArchivePrunableSlot.get() > 0) {
      final long tillSlotLong = lastDataColumnSidecarArchivePrunableSlot.get();
      final long earliestSlotLong =
          database.getEarliestDataColumnSidecarSlot().map(UInt64::longValue).orElse(0L);
      if (earliestSlotLong > tillSlotLong) {
        LOG.debug(
            "No data column sidecars to archive: earliest stored slot {} is after prunable till slot {}",
            earliestSlotLong,
            tillSlotLong);
        return;
      }
      final RangeSet<Long> unarchived =
          archivedSlotRanges.complement().subRangeSet(Range.closed(earliestSlotLong, tillSlotLong));
      if (unarchived.isEmpty()) {
        LOG.debug(
            "All data column sidecars up to slot {} have been archived to proofs", tillSlotLong);
        return;
      }
      // Take the highest (most recent) unarchived range; ContiguousSet resolves inclusive bounds.
      final Range<Long> highestGap = Iterables.getLast(unarchived.asRanges());
      final ContiguousSet<Long> gapSlots = ContiguousSet.create(highestGap, DiscreteDomain.longs());
      if (gapSlots.isEmpty()) {
        return;
      }
      final long upper = gapSlots.last();
      final long lower = Math.max(gapSlots.first(), upper - pruneLimit);
      LOG.debug(
          "Archiving data column sidecars to proofs from slot {} up to slot {} (limit {})",
          lower,
          upper,
          pruneLimit);
      database.archiveSidecarsProofs(UInt64.valueOf(lower), UInt64.valueOf(upper));
      // Store as half-open [lower, upper+1) so adjacent chunks connect and merge in the RangeSet.
      // Range<Long> is real-valued: [9,19] and [20,30] are not connected, which would leave a
      // phantom gap (19,20) that is empty but gets picked as the highest unarchived range.
      archivedSlotRanges.add(Range.closedOpen(lower, upper + 1L));
    }
  }

  @Override
  public void onSidecarArchivePrunableSlot(final UInt64 slot) {
    lastDataColumnSidecarArchivePrunableSlot.set(slot.longValue());
  }

  /**
   * This method adds an extra epoch following the rational of the edge case found in the
   * BlobSidecarPruner. See {@link
   * tech.pegasys.teku.storage.server.pruner.BlobSidecarPruner#getLatestPrunableSlot(UInt64)}
   */
  private UInt64 calculatePruneSlot() {
    final UInt64 currentSlot =
        spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime.get());
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
    final int custodyPeriodEpochs =
        spec.getSpecConfig(currentEpoch)
            .toVersionFulu()
            .map(SpecConfigFulu::getMinEpochsForDataColumnSidecarsRequests)
            .orElse(0);
    if (custodyPeriodEpochs == 0) {
      return currentSlot;
    } else {
      final UInt64 minCustodyEpoch = currentEpoch.minusMinZero(custodyPeriodEpochs + 1);
      return spec.computeStartSlotAtEpoch(minCustodyEpoch);
    }
  }

  private Optional<UInt64> getGenesisTime() {
    if (genesisTime.isPresent()) {
      return genesisTime;
    }
    genesisTime = database.getGenesisTime();
    return genesisTime;
  }
}
