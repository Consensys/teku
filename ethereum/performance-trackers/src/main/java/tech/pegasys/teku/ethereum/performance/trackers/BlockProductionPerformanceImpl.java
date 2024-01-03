/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.ethereum.performance.trackers;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.PerformanceTracker;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockProductionPerformanceImpl implements BlockProductionPerformance {
  private final PerformanceTracker performanceTracker;
  private final UInt64 slot;
  private UInt64 slotTime = UInt64.ZERO;
  private final int lateThreshold;

  public BlockProductionPerformanceImpl(
      final TimeProvider timeProvider, final UInt64 slot, final int lateThreshold) {
    this.performanceTracker = new PerformanceTracker(timeProvider);
    this.lateThreshold = lateThreshold;
    this.slot = slot;
    performanceTracker.addEvent("start");
  }

  @Override
  public void slotTime(final Supplier<UInt64> slotTimeSupplier) {
    this.slotTime = slotTimeSupplier.get();
  }

  @Override
  public void complete() {
    if (slotTime.isZero()) {
      // we haven't managed to calculate slot time, something wrong happened
      return;
    }
    final UInt64 completionTime = performanceTracker.addEvent(COMPLETE_LABEL);
    final boolean isLateEvent = completionTime.minusMinZero(slotTime).isGreaterThan(lateThreshold);
    performanceTracker.report(
        slotTime,
        isLateEvent,
        (event, stepDuration) -> {},
        totalDuration -> {},
        (totalDuration, timings) ->
            EventLogger.EVENT_LOG.slowBlockProductionEvent(slot, totalDuration, timings));
  }

  @Override
  public void prepareOnTick() {
    performanceTracker.addEvent("preparation_on_tick");
  }

  @Override
  public void prepareApplyDeferredAttestations() {
    performanceTracker.addEvent("preparation_apply_deferred_attestations");
  }

  @Override
  public void prepareProcessHead() {
    performanceTracker.addEvent("preparation_process_head");
  }

  @Override
  public void beaconBlockPrepared() {
    performanceTracker.addEvent("beacon_block_prepared");
  }

  @Override
  public void getStateAtSlot() {
    performanceTracker.addEvent("retrieve_state");
  }

  @Override
  public void engineGetPayload() {
    performanceTracker.addEvent("local_get_payload");
  }

  @Override
  public void builderGetHeader() {
    performanceTracker.addEvent("builder_get_header");
  }

  @Override
  public void builderBidValidated() {
    performanceTracker.addEvent("builder_bid_validated");
  }

  @Override
  public void beaconBlockCreated() {
    performanceTracker.addEvent("beacon_block_created");
  }

  @Override
  public void stateTransition() {
    performanceTracker.addEvent("state_transition");
  }

  @Override
  public void stateHashing() {
    performanceTracker.addEvent("state_hashing");
  }
}
