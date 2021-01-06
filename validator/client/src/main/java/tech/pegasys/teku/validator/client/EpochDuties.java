/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.validator.client;

import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

class EpochDuties {
  private static final Logger LOG = LogManager.getLogger();

  private final List<Consumer<ScheduledDuties>> pendingActions = new ArrayList<>();
  private final DutyLoader dutyLoader;
  private final UInt64 epoch;
  private SafeFuture<Optional<ScheduledDuties>> duties = new SafeFuture<>();

  private EpochDuties(final DutyLoader dutyLoader, final UInt64 epoch) {
    this.dutyLoader = dutyLoader;
    this.epoch = epoch;
  }

  public static EpochDuties calculateDuties(final DutyLoader dutyLoader, final UInt64 epoch) {
    final EpochDuties duties = new EpochDuties(dutyLoader, epoch);
    duties.recalculate();
    return duties;
  }

  public void onBlockProductionDue(final UInt64 slot) {
    execute(duties -> duties.produceBlock(slot));
  }

  public void onAttestationCreationDue(final UInt64 slot) {
    execute(duties -> duties.produceAttestations(slot));
  }

  public void onAttestationAggregationDue(final UInt64 slot) {
    execute(duties -> duties.performAggregation(slot));
  }

  public int countDuties() {
    return getCurrentDuties().map(ScheduledDuties::countDuties).orElse(0);
  }

  public synchronized void recalculate() {
    duties.cancel(false);
    // We need to ensure the duties future is completed before .
    duties = dutyLoader.loadDutiesForEpoch(epoch);
    duties.finish(
        this::processPendingActions,
        error -> {
          if (!(Throwables.getRootCause(error) instanceof CancellationException)) {
            LOG.error("Failed to load duties", error);
          } else {
            LOG.trace("Loading duties cancelled", error);
          }
        });
  }

  public synchronized void cancel() {
    duties.cancel(false);
    pendingActions.clear();
  }

  private void processPendingActions(final Optional<ScheduledDuties> scheduledDuties) {
    scheduledDuties.ifPresent(duties -> pendingActions.forEach(action -> action.accept(duties)));
    pendingActions.clear();
  }

  private synchronized void execute(final Consumer<ScheduledDuties> action) {
    getCurrentDuties().ifPresentOrElse(action, () -> pendingActions.add(action));
  }

  private synchronized Optional<ScheduledDuties> getCurrentDuties() {
    if (!duties.isCompletedNormally()) {
      return Optional.empty();
    }
    return duties.join();
  }
}
