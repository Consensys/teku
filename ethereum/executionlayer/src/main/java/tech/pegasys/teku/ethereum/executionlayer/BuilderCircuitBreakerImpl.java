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

package tech.pegasys.teku.ethereum.executionlayer;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.BuilderCircuitBreakerUtil;

public class BuilderCircuitBreakerImpl implements BuilderCircuitBreaker {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final int faultInspectionWindow;
  private final int minimumUniqueBlockRootsInWindow;
  private final int consecutiveAllowedFaults;

  public BuilderCircuitBreakerImpl(
      final Spec spec,
      final int faultInspectionWindow,
      final int allowedFaults,
      final int consecutiveAllowedFaults) {
    checkArgument(
        faultInspectionWindow > allowedFaults,
        "FaultInspectionWindow must be greater than AllowedFaults");
    this.spec = spec;
    this.faultInspectionWindow = faultInspectionWindow;
    this.minimumUniqueBlockRootsInWindow = faultInspectionWindow - allowedFaults;
    this.consecutiveAllowedFaults = consecutiveAllowedFaults;
  }

  @Override
  public boolean isEngaged(final BeaconState state) {

    final BuilderCircuitBreakerUtil.InspectionWindowCounters inspectionWindowCounters =
        getInspectionWindowCounters(state);
    if (inspectionWindowCounters.uniqueBlockRootsCount() < minimumUniqueBlockRootsInWindow) {
      LOG.debug(
          "Builder circuit breaker engaged: slot: {}, uniqueBlockRootsCount: {}, window: {},  minimumUniqueBlockRootsInWindow: {}",
          state.getSlot(),
          inspectionWindowCounters.uniqueBlockRootsCount(),
          faultInspectionWindow,
          minimumUniqueBlockRootsInWindow);
      return true;
    }

    if (inspectionWindowCounters.lastConsecutiveEmptySlots() > consecutiveAllowedFaults) {
      LOG.debug(
          "Builder circuit breaker engaged: slot: {}, lastConsecutiveEmptySlots: {}, window: {},  consecutiveAllowedFaults: {}",
          state.getSlot(),
          inspectionWindowCounters.lastConsecutiveEmptySlots(),
          faultInspectionWindow,
          consecutiveAllowedFaults);
      return true;
    }

    LOG.debug("Builder circuit breaker has not engaged.");

    return false;
  }

  @VisibleForTesting
  BuilderCircuitBreakerUtil.InspectionWindowCounters getInspectionWindowCounters(
      final BeaconState state) throws IllegalArgumentException {
    final int slotsPerHistoricalRoot =
        spec.atSlot(state.getSlot()).getConfig().getSlotsPerHistoricalRoot();
    return spec.getBuilderCircuitBreakerUtil(state.getSlot())
        .getInspectionWindowCounters(state, faultInspectionWindow, slotsPerHistoricalRoot);
  }
}
