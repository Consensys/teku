/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.util;

import static tech.pegasys.teku.spec.constants.SpecConstants.GENESIS_EPOCH;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;

public class ChainTimingUtil {
  private final SpecConstants specConstants;

  public ChainTimingUtil(final SpecConstants specConstants) {
    this.specConstants = specConstants;
  }

  public UInt64 computeEpochAtSlot(UInt64 slot) {
    // TODO this should take into account hard forks
    return slot.dividedBy(specConstants.getSlotsPerEpoch());
  }

  public UInt64 getCurrentEpoch(BeaconState state) {
    return computeEpochAtSlot(state.getSlot());
  }

  UInt64 getNextEpoch(BeaconState state) {
    return getCurrentEpoch(state).plus(UInt64.ONE);
  }

  public UInt64 getPreviousEpoch(BeaconState state) {
    UInt64 currentEpoch = getCurrentEpoch(state);
    return currentEpoch.equals(GENESIS_EPOCH) ? GENESIS_EPOCH : currentEpoch.minus(UInt64.ONE);
  }

  public UInt64 computeNextEpochBoundary(final UInt64 slot) {
    final UInt64 currentEpoch = computeEpochAtSlot(slot);
    return computeStartSlotAtEpoch(currentEpoch).equals(slot) ? currentEpoch : currentEpoch.plus(1);
  }

  public UInt64 computeStartSlotAtEpoch(UInt64 epoch) {
    return epoch.times(specConstants.getSlotsPerEpoch());
  }
}
