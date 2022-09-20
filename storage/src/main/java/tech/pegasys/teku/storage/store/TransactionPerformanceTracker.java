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

package tech.pegasys.teku.storage.store;

import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.PerformanceTracker;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class TransactionPerformanceTracker {
  public static final String COMPLETE_LABEL = "complete";
  public static final int LATE_EVENT_MS = 500;
  private final PerformanceTracker performanceTracker;
  private final UInt64 startTime;

  public TransactionPerformanceTracker(final TimeProvider timeProvider, final UInt64 startTime) {
    this.performanceTracker = new PerformanceTracker(timeProvider);
    this.startTime = startTime;
    performanceTracker.addEvent("start");
  }

  public void retrieveLastFinalizedCompleted() {
    performanceTracker.addEvent("retrieve_last_finalized_completed");
  }

  public void writeLockAcquired() {
    performanceTracker.addEvent("write_lock_acquired");
  }

  public void updatesCreated(final String details) {
    performanceTracker.addEvent("updates_created_" + details);
  }

  public void applyToStoreWriteLockAcquiring() {
    performanceTracker.addEvent("apply_to_store_write_lock_acquiring");
  }

  public void applyToStoreWriteLockAcquired() {
    performanceTracker.addEvent("apply_to_store_write_lock_acquired");
  }

  public void applyToStoreCompleted() {
    performanceTracker.addEvent("apply_to_store_completed");
  }

  public void applyToStoreOnNewFinalizedCompleted() {
    performanceTracker.addEvent("apply_to_store_on_new_finalized");
  }

  public void complete() {
    final UInt64 completionTime = performanceTracker.addEvent(COMPLETE_LABEL);
    final boolean isLateEvent = completionTime.minusMinZero(startTime).isGreaterThan(LATE_EVENT_MS);
    performanceTracker.report(
        startTime,
        isLateEvent,
        (event, stepDuration) -> {},
        totalDuration -> {},
        (totalDuration, timings) ->
            EventLogger.EVENT_LOG.slowTxEvent(startTime, totalDuration, timings));
  }
}
