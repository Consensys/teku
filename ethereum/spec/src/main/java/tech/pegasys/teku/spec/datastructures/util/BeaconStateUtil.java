/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.util;

import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

@Deprecated
public class BeaconStateUtil {

  /**
   * Returns the epoch number of the given slot.
   *
   * @param slot - The slot number under consideration.
   * @return The epoch associated with the given slot number.
   * @see <a>
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_epoch_of_slot</a>
   */
  @Deprecated
  public static UInt64 compute_epoch_at_slot(UInt64 slot) {
    return slot.dividedBy(Constants.SLOTS_PER_EPOCH);
  }

  /**
   * Return the starting slot of the given epoch.
   *
   * @param epoch - The epoch under consideration.
   * @return The slot that the given epoch starts at.
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_epoch_of_slot</a>
   */
  @Deprecated
  public static UInt64 compute_start_slot_at_epoch(UInt64 epoch) {
    return epoch.times(SLOTS_PER_EPOCH);
  }
}
