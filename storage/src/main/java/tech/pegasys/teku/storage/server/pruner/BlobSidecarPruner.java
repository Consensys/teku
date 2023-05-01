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

package tech.pegasys.teku.storage.server.pruner;

import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.ShuttingDownException;

public class BlobSidecarPruner extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Database database;
  private final AsyncRunner asyncRunner;
  private final Duration pruneInterval;
  private final int pruneLimit;
  private final TimeProvider timeProvider;
  private final boolean blobSidecarStorageCountersEnabled;

  private Optional<Cancellable> scheduledPruner = Optional.empty();
  private Optional<UInt64> genesisTime = Optional.empty();

  private final AtomicLong blobColumnSize = new AtomicLong(0);
  private final AtomicLong earliestBlobSidecarSlot = new AtomicLong(-1);

  public BlobSidecarPruner(
      final Spec spec,
      final Database database,
      final MetricsSystem metricsSystem,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final Duration pruneInterval,
      final int pruneLimit,
      final boolean blobSidecarStorageCountersEnabled) {
    this.spec = spec;
    this.database = database;
    this.asyncRunner = asyncRunner;
    this.pruneInterval = pruneInterval;
    this.pruneLimit = pruneLimit;
    this.timeProvider = timeProvider;
    this.blobSidecarStorageCountersEnabled = blobSidecarStorageCountersEnabled;

    if (blobSidecarStorageCountersEnabled) {
      LabelledGauge labelledGauge =
          metricsSystem.createLabelledGauge(
              TekuMetricCategory.STORAGE,
              "blob_sidecars",
              "Statistics for BlobSidecars stored",
              "type");

      labelledGauge.labels(blobColumnSize::get, "total");
      labelledGauge.labels(earliestBlobSidecarSlot::get, "earliest_slot");
    }
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    scheduledPruner =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::pruneBlobs,
                Duration.ZERO,
                pruneInterval,
                error -> LOG.error("Failed to prune old blobs", error)));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    scheduledPruner.ifPresent(Cancellable::cancel);
    return SafeFuture.COMPLETE;
  }

  private void pruneBlobs() {
    pruneBlobsPriorToAvailabilityWindow();

    if (blobSidecarStorageCountersEnabled) {
      blobColumnSize.set(database.getBlobSidecarColumnCount());
      earliestBlobSidecarSlot.set(
          database.getEarliestBlobSidecarSlot().map(UInt64::longValue).orElse(-1L));
    }
  }

  private void pruneBlobsPriorToAvailabilityWindow() {
    final Optional<UInt64> genesisTime = getGenesisTime();
    if (genesisTime.isEmpty()) {
      LOG.debug("Not pruning as no genesis time is available.");
      return;
    }

    final UInt64 currentSlot =
        spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime.get());

    final UInt64 latestPrunableSlot = getLatestPrunableSlot(currentSlot);

    if (latestPrunableSlot.isZero()) {
      LOG.debug("Not pruning as slots to keep include genesis.");
      return;
    }
    LOG.debug("Pruning blobs up to slot {}, limit {}", latestPrunableSlot, pruneLimit);
    try {
      final long start = System.currentTimeMillis();
      final boolean limitReached = database.pruneOldestBlobSidecars(latestPrunableSlot, pruneLimit);
      LOG.debug(
          "Blobs pruning finished in {} ms. Limit reached: {}",
          () -> System.currentTimeMillis() - start,
          () -> limitReached);
    } catch (ShuttingDownException | RejectedExecutionException ex) {
      LOG.debug("Shutting down", ex);
    }
  }

  private Optional<UInt64> getGenesisTime() {
    if (genesisTime.isPresent()) {
      return genesisTime;
    }
    genesisTime = database.getGenesisTime();
    return genesisTime;
  }

  private UInt64 getLatestPrunableSlot(final UInt64 currentSlot) {
    // we have to guarantee that current epoch data is fully available,
    // moreover we want to gradually delete blobs at each iteration

    // MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS = 1
    //    (5)
    //  latest                        (70)
    //  prunable   DA boundary          current_slot
    //     |       |                    |
    // 0 ----- 31 // 32 ---- 63 // 64 ---- 95 // 96

    // edge case 1 (only 1 slot of tolerance to support slight client timing differences):
    //   current_slot = 95
    //   DA_boundary: 32
    //   latest_prunable_slot = 30

    // edge case 2 (1 entire epoch of extra data):
    //   current_slot = 96
    //   DA_boundary: 64
    //   latest_prunable_slot = 31

    return currentSlot.minusMinZero(
        ((long) (MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS + 1)
                * spec.atSlot(currentSlot).getSlotsPerEpoch())
            + 1);
  }
}
