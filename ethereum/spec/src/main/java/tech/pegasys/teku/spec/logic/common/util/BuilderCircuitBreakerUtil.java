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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public abstract class BuilderCircuitBreakerUtil {
  public static final BuilderCircuitBreakerUtil BLOCK_ROOT =
      new BlockRootBuilderCircuitBreakerUtil();

  public static final BuilderCircuitBreakerUtil NOOP =
      new BuilderCircuitBreakerUtil() {
        @Override
        public InspectionWindowCounters getInspectionWindowCounters(
            final BeaconState state,
            final int faultInspectionWindow,
            final int slotsPerHistoricalRoot) {
          return new InspectionWindowCounters(Integer.MAX_VALUE, 0);
        }
      };

  public abstract InspectionWindowCounters getInspectionWindowCounters(
      BeaconState state, int faultInspectionWindow, int slotsPerHistoricalRoot);

  public record InspectionWindowCounters(
      int uniqueBlockRootsCount, int lastConsecutiveEmptySlots) {}

  private static class BlockRootBuilderCircuitBreakerUtil extends BuilderCircuitBreakerUtil {
    @Override
    public InspectionWindowCounters getInspectionWindowCounters(
        final BeaconState state,
        final int faultInspectionWindow,
        final int slotsPerHistoricalRoot) {
      checkArgument(
          faultInspectionWindow <= slotsPerHistoricalRoot,
          "faultInspectionWindow (%s) cannot exceed slotsPerHistoricalRoot config (%s)",
          faultInspectionWindow,
          slotsPerHistoricalRoot);

      final HashSet<Bytes32> uniqueBlockRoots = new HashSet<>();
      final SszBytes32Vector blockRoots = state.getBlockRoots();
      final UInt64 firstSlotOfInspectionWindow =
          state.getSlot().minusMinZero(faultInspectionWindow);
      final UInt64 lastSlotOfInspectionWindow = state.getSlot().minusMinZero(1);

      final int lastConsecutiveEmptySlots =
          lastSlotOfInspectionWindow.minus(state.getLatestBlockHeader().getSlot()).intValue();

      if (lastConsecutiveEmptySlots >= faultInspectionWindow) {
        return new InspectionWindowCounters(0, lastConsecutiveEmptySlots);
      }

      uniqueBlockRoots.add(state.getLatestBlockHeader().getRoot());

      UInt64 currentSlot = firstSlotOfInspectionWindow;
      while (currentSlot.isLessThan(lastSlotOfInspectionWindow)) {
        final int currentBlockRootIndex = currentSlot.mod(slotsPerHistoricalRoot).intValue();
        uniqueBlockRoots.add(blockRoots.getElement(currentBlockRootIndex));
        currentSlot = currentSlot.increment();
      }

      return new InspectionWindowCounters(uniqueBlockRoots.size(), lastConsecutiveEmptySlots);
    }
  }
}
