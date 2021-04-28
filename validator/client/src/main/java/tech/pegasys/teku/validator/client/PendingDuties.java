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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

class PendingDuties {
  private static final Logger LOG = LogManager.getLogger();

  private final List<Consumer<ScheduledDuties>> pendingActions = new ArrayList<>();
  private final DutyLoader<?> dutyLoader;
  private final UInt64 epoch;
  private SafeFuture<? extends Optional<? extends ScheduledDuties>> duties = new SafeFuture<>();
  private Optional<Bytes32> pendingHeadUpdate = Optional.empty();

  private PendingDuties(final DutyLoader<?> dutyLoader, final UInt64 epoch) {
    this.dutyLoader = dutyLoader;
    this.epoch = epoch;
  }

  public static PendingDuties calculateDuties(final DutyLoader<?> dutyLoader, final UInt64 epoch) {
    final PendingDuties duties = new PendingDuties(dutyLoader, epoch);
    duties.recalculate();
    return duties;
  }

  public void onProductionDue(final UInt64 slot) {
    execute(duties -> duties.performProductionDuty(slot));
  }

  public void onAggregationDue(final UInt64 slot) {
    execute(duties -> duties.performAggregationDuty(slot));
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

  private synchronized void processPendingActions(
      final Optional<? extends ScheduledDuties> scheduledDuties) {
    if (pendingHeadUpdate.isPresent()
        && scheduledDuties.isPresent()
        && scheduledDuties.get().requiresRecalculation(pendingHeadUpdate.get())) {
      pendingHeadUpdate = Optional.empty();
      recalculate();
      return;
    }
    pendingHeadUpdate = Optional.empty();
    scheduledDuties.ifPresent(duties -> pendingActions.forEach(action -> action.accept(duties)));
    pendingActions.clear();
  }

  protected synchronized void execute(final Consumer<ScheduledDuties> action) {
    getCurrentDuties().ifPresentOrElse(action, () -> pendingActions.add(action));
  }

  private synchronized Optional<? extends ScheduledDuties> getCurrentDuties() {
    if (!duties.isCompletedNormally()) {
      return Optional.empty();
    }
    return duties.join();
  }

  public synchronized void onHeadUpdate(final Bytes32 newHeadDependentRoot) {
    getCurrentDuties()
        .ifPresentOrElse(
            duties -> {
              if (duties.requiresRecalculation(newHeadDependentRoot)) {
                recalculate();
              }
            },
            () -> pendingHeadUpdate = Optional.of(newHeadDependentRoot));
  }
}
