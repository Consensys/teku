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

package tech.pegasys.teku.statetransition.execution;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GloasExecutionPayloadBidCircuitBreaker implements ExecutionPayloadBidCircuitBreaker {
  private static final Logger LOG = LogManager.getLogger();

  private final int faultInspectionWindow;
  private final int allowedFaults;
  private final int consecutiveAllowedFaults;
  private final Supplier<Optional<ReadOnlyForkChoiceStrategy>> forkChoiceStrategySupplier;

  public GloasExecutionPayloadBidCircuitBreaker(
      final int faultInspectionWindow,
      final int allowedFaults,
      final int consecutiveAllowedFaults,
      final Supplier<Optional<ReadOnlyForkChoiceStrategy>> forkChoiceStrategySupplier) {
    checkArgument(
        faultInspectionWindow > allowedFaults,
        "FaultInspectionWindow must be greater than AllowedFaults");
    this.faultInspectionWindow = faultInspectionWindow;
    this.allowedFaults = allowedFaults;
    this.consecutiveAllowedFaults = consecutiveAllowedFaults;
    this.forkChoiceStrategySupplier = forkChoiceStrategySupplier;
  }

  @Override
  public boolean isEngaged(final Bytes32 parentRoot, final BeaconState state) {
    final Optional<ReadOnlyForkChoiceStrategy> maybeForkChoiceStrategy =
        forkChoiceStrategySupplier.get();
    if (maybeForkChoiceStrategy.isEmpty()) {
      LOG.debug(
          "Gloas builder circuit breaker engaged because fork choice data is unavailable at slot {}",
          state.getSlot());
      return true;
    }

    final InspectionWindowCounters inspectionWindowCounters =
        getInspectionWindowCounters(maybeForkChoiceStrategy.get(), parentRoot, state.getSlot());
    if (inspectionWindowCounters.forkChoiceDataUnavailable()) {
      LOG.debug(
          "Gloas builder circuit breaker engaged because fork choice data is incomplete for parent root {} at slot {}",
          parentRoot,
          state.getSlot());
      return true;
    }

    if (inspectionWindowCounters.unavailablePayloadsCount() > allowedFaults) {
      LOG.debug(
          "Gloas builder circuit breaker engaged: slot: {}, unavailablePayloadsCount: {}, window: {}, allowedFaults: {}",
          state.getSlot(),
          inspectionWindowCounters.unavailablePayloadsCount(),
          faultInspectionWindow,
          allowedFaults);
      return true;
    }

    if (inspectionWindowCounters.lastConsecutiveUnavailablePayloads() > consecutiveAllowedFaults) {
      LOG.debug(
          "Gloas builder circuit breaker engaged: slot: {}, lastConsecutiveUnavailablePayloads: {}, window: {}, consecutiveAllowedFaults: {}",
          state.getSlot(),
          inspectionWindowCounters.lastConsecutiveUnavailablePayloads(),
          faultInspectionWindow,
          consecutiveAllowedFaults);
      return true;
    }

    LOG.debug("Gloas builder circuit breaker has not engaged.");
    return false;
  }

  @VisibleForTesting
  InspectionWindowCounters getInspectionWindowCounters(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 parentRoot,
      final UInt64 slot) {
    if (!forkChoiceStrategy.contains(parentRoot)) {
      return InspectionWindowCounters.unavailableForkChoiceData();
    }

    final Set<Bytes32> inspectedBlockRoots = new HashSet<>();
    int unavailablePayloadsCount = 0;
    int lastConsecutiveUnavailablePayloads = 0;
    boolean isCountingConsecutiveUnavailablePayloads = true;

    final UInt64 firstSlotOfInspectionWindow = slot.minusMinZero(faultInspectionWindow);
    UInt64 currentSlot = slot.minusMinZero(1);
    while (currentSlot.isGreaterThanOrEqualTo(firstSlotOfInspectionWindow)) {
      final Optional<Bytes32> maybeAncestorRoot =
          forkChoiceStrategy.getAncestor(parentRoot, currentSlot);
      if (maybeAncestorRoot.isPresent() && inspectedBlockRoots.add(maybeAncestorRoot.get())) {
        final Bytes32 ancestorRoot = maybeAncestorRoot.get();
        final Optional<UInt64> maybeAncestorSlot = forkChoiceStrategy.blockSlot(ancestorRoot);
        if (maybeAncestorSlot.isEmpty()) {
          return InspectionWindowCounters.unavailableForkChoiceData();
        }
        if (maybeAncestorSlot.get().isGreaterThanOrEqualTo(firstSlotOfInspectionWindow)) {
          if (hasAvailablePayload(forkChoiceStrategy, ancestorRoot)) {
            isCountingConsecutiveUnavailablePayloads = false;
          } else {
            unavailablePayloadsCount++;
            if (isCountingConsecutiveUnavailablePayloads) {
              lastConsecutiveUnavailablePayloads++;
            }
          }
        }
      }
      if (currentSlot.isZero()) {
        break;
      }
      currentSlot = currentSlot.minusMinZero(1);
    }

    return new InspectionWindowCounters(
        unavailablePayloadsCount, lastConsecutiveUnavailablePayloads, false);
  }

  private boolean hasAvailablePayload(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy, final Bytes32 ancestorRoot) {
    return forkChoiceStrategy
        .getBlockData(ancestorRoot, PAYLOAD_STATUS_FULL)
        .map(nodeData -> nodeData.getValidationStatus() == ProtoNodeValidationStatus.VALID)
        .orElse(false);
  }

  record InspectionWindowCounters(
      int unavailablePayloadsCount,
      int lastConsecutiveUnavailablePayloads,
      boolean forkChoiceDataUnavailable) {

    static InspectionWindowCounters unavailableForkChoiceData() {
      return new InspectionWindowCounters(Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    }
  }
}
