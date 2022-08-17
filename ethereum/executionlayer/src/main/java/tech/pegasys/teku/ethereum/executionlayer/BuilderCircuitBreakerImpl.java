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

package tech.pegasys.teku.ethereum.executionlayer;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class BuilderCircuitBreakerImpl implements BuilderCircuitBreaker {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final int faultInspectionWindow;
  private final int minimumUniqueBlockRootsInWindow;

  public BuilderCircuitBreakerImpl(
      final Spec spec, final int faultInspectionWindow, final int allowedFaults) {
    checkArgument(
        faultInspectionWindow > allowedFaults,
        "FaultInspectionWindow must be greater than AllowedFaults");
    this.spec = spec;
    this.faultInspectionWindow = faultInspectionWindow;
    this.minimumUniqueBlockRootsInWindow = faultInspectionWindow - allowedFaults;
  }

  @Override
  public boolean isEngaged(final BeaconState state) {
    try {
      if (getLatestUniqueBlockRootsCount(state) <= minimumUniqueBlockRootsInWindow) {
        return true;
      }
    } catch (Exception ex) {
      LOG.error("Builder circuit breaker check failed. Acting like it has been engaged.", ex);
      return true;
    }

    return false;
  }

  @VisibleForTesting
  int getLatestUniqueBlockRootsCount(final BeaconState state) throws IllegalArgumentException {
    final int slotsPerHistoricalRoot =
        spec.atSlot(state.getSlot()).getConfig().getSlotsPerHistoricalRoot();
    checkArgument(
        faultInspectionWindow <= slotsPerHistoricalRoot,
        "faultInspectionWindow (%s) cannot exceed slotsPerHistoricalRoot config (%s)",
        faultInspectionWindow,
        slotsPerHistoricalRoot);

    final HashSet<Bytes32> uniqueBlockRoots = new HashSet<>();
    final SszBytes32Vector blockRoots = state.getBlockRoots();

    // we subtract 1 because blockRoots refers to previous block
    // current is getLatestBlockHeader
    final UInt64 firstSlotOfInspectionWindow =
        state.getSlot().minusMinZero(faultInspectionWindow - 1);

    UInt64 currentSlot = firstSlotOfInspectionWindow;
    while (currentSlot.isLessThan(state.getSlot())) {
      final int currentBlockRootIndex = currentSlot.mod(slotsPerHistoricalRoot).intValue();
      uniqueBlockRoots.add(blockRoots.getElement(currentBlockRootIndex));
      currentSlot = currentSlot.increment();
    }

    // we add getLatestBlockHeader only if it's slot fall into the window
    // otherwise we remove it from the count
    if (state
        .getLatestBlockHeader()
        .getSlot()
        .isGreaterThanOrEqualTo(firstSlotOfInspectionWindow)) {
      uniqueBlockRoots.add(state.getLatestBlockHeader().getRoot());
    } else {
      uniqueBlockRoots.remove(state.getLatestBlockHeader().getRoot());
    }

    return uniqueBlockRoots.size();
  }
}
