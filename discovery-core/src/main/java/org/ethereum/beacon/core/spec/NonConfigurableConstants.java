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

package org.ethereum.beacon.core.spec;

import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Time;
import tech.pegasys.artemis.util.uint.UInt64;

public interface NonConfigurableConstants {
  EpochNumber FAR_FUTURE_EPOCH = EpochNumber.castFrom(UInt64.MAX_VALUE); // (1 << 64) - 1
  UInt64 BASE_REWARDS_PER_EPOCH = UInt64.valueOf(5);
  UInt64 DEPOSIT_CONTRACT_TREE_DEPTH = UInt64.valueOf(1 << 5); // 32
  Time SECONDS_PER_DAY = Time.of(86400);
  int JUSTIFICATION_BITS_LENGTH = 4;

  default EpochNumber getFarFutureEpoch() {
    return FAR_FUTURE_EPOCH;
  }

  default UInt64 getBaseRewardsPerEpoch() {
    return BASE_REWARDS_PER_EPOCH;
  }

  default UInt64 getDepositContractTreeDepth() {
    return DEPOSIT_CONTRACT_TREE_DEPTH;
  }

  /**
   * Used in vector size specification, search for string spec.DEPOSIT_CONTRACT_TREE_DEPTH_PLUS_ONE
   */
  default UInt64 getDepositContractTreeDepthPlusOne() {
    return getDepositContractTreeDepth().increment();
  }

  default Time getSecondsPerDay() {
    return SECONDS_PER_DAY;
  }

  default int getJustificationBitsLength() {
    return JUSTIFICATION_BITS_LENGTH;
  }
}
