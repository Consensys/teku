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

package tech.pegasys.teku.core;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ChainProperties {

  /**
   * Given a slot, returns the earliest epoch that can be finalized with a block at this slot. If
   * the slot is at the start of an epoch, return the current epoch. Otherwise, return the next
   * epoch.
   *
   * @param slot The slot we want to finalize.
   * @return The earliest epoch that can be finalized at this slot.
   */
  public static UInt64 computeBestEpochFinalizableAtSlot(long slot) {
    return computeBestEpochFinalizableAtSlot(UInt64.valueOf(slot));
  }

  /**
   * Given a slot, returns the earliest epoch that can be finalized with a block at this slot. If
   * the slot is at the start of an epoch, return the current epoch. Otherwise, return the next
   * epoch.
   *
   * @param slot The slot we want to finalize.
   * @return The earliest epoch that can be finalized at this slot.
   */
  public static UInt64 computeBestEpochFinalizableAtSlot(UInt64 slot) {
    final UInt64 currentEpoch = compute_epoch_at_slot(slot);
    final UInt64 startSlotAtCurrentEpoch = compute_start_slot_at_epoch(currentEpoch);
    return startSlotAtCurrentEpoch.equals(slot) ? currentEpoch : currentEpoch.plus(UInt64.ONE);
  }
}
