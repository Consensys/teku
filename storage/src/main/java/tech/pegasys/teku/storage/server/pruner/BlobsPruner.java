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

import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS;

import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.Database;

public class BlobsPruner extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Database database;
  private final AsyncRunner asyncRunner;
  private final Duration pruneInterval;
  private int pruneLimit;
  private final TimeProvider timeProvider;

  private Optional<Cancellable> scheduledPruner = Optional.empty();
  private Optional<UInt64> genesisTime = Optional.empty();

  public BlobsPruner(
      final Spec spec,
      final Database database,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final Duration pruneInterval,
      final int pruneLimit) {
    this.spec = spec;
    this.database = database;
    this.asyncRunner = asyncRunner;
    this.pruneInterval = pruneInterval;
    this.pruneLimit = pruneLimit;
    this.timeProvider = timeProvider;
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
    final Optional<UInt64> genesisTime = getGenesisTime();
    if (genesisTime.isEmpty()) {
      LOG.debug("Not pruning as no genesis time is available.");
      return;
    }

    final UInt64 currentSlot =
        spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime.get());

    final UInt64 earliestPrunableSlot = getEarliestPrunableSlot(currentSlot);

    if (earliestPrunableSlot.isZero()) {
      LOG.debug("Not pruning as slots to keep includes genesis.");
      return;
    }
    LOG.debug("Pruning blobs up to slot {}, limit {}", earliestPrunableSlot, pruneLimit);
    final long start = System.currentTimeMillis();
    final boolean limitReached = database.pruneOldestBlobsSidecar(earliestPrunableSlot, pruneLimit);
    LOG.debug(
        "Blobs pruning finished in {} ms. Limit reached: {}",
        () -> System.currentTimeMillis() - start,
        () -> limitReached);
  }

  private Optional<UInt64> getGenesisTime() {
    if (genesisTime.isPresent()) {
      return genesisTime;
    }
    genesisTime = database.getGenesisTime();
    return genesisTime;
  }

  private UInt64 getEarliestPrunableSlot(final UInt64 currentSlot) {
    final int slotsPerEpoch = spec.getSlotsPerEpoch(currentSlot);

    return currentSlot.minusMinZero((long) MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS * slotsPerEpoch);
  }
}
