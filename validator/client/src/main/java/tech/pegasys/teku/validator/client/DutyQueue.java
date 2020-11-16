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

class DutyQueue {
  private static final Logger LOG = LogManager.getLogger();

  private final SafeFuture<ScheduledDuties> futureDuties;
  private final List<Consumer<ScheduledDuties>> pendingActions = new ArrayList<>();
  private Optional<ScheduledDuties> duties = Optional.empty();

  DutyQueue(final SafeFuture<ScheduledDuties> futureDuties) {
    this.futureDuties = futureDuties;
    futureDuties.finish(
        this::onDutiesLoaded,
        error -> {
          if (!(Throwables.getRootCause(error) instanceof CancellationException)) {
            LOG.error("Failed to load duties", error);
          }
        });
  }

  public synchronized Optional<Bytes32> getTargetRoot() {
    return duties.map(ScheduledDuties::getTargetRoot);
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
    return duties.map(ScheduledDuties::countDuties).orElse(0);
  }

  public synchronized void cancel() {
    futureDuties.cancel(false);
    pendingActions.clear();
  }

  private synchronized void onDutiesLoaded(final ScheduledDuties scheduledDuties) {
    duties = Optional.of(scheduledDuties);
    pendingActions.forEach(action -> action.accept(scheduledDuties));
    pendingActions.clear();
  }

  private synchronized void execute(final Consumer<ScheduledDuties> action) {
    this.duties.ifPresentOrElse(action, () -> pendingActions.add(action));
  }
}
