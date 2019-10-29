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

import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Quotients that are used in reward and penalties calculation.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#reward-and-penalty-quotients">Reward
 *     and penalty quotients</a> in the spec.
 */
public interface RewardAndPenaltyQuotients {

  UInt64 BASE_REWARD_FACTOR = UInt64.valueOf(1 << 6); // 2048
  UInt64 WHISTLEBLOWER_REWARD_QUOTIENT = UInt64.valueOf(1 << 9); // 512
  UInt64 PROPOSER_REWARD_QUOTIENT = UInt64.valueOf(1 << 3); // 8
  UInt64 INACTIVITY_PENALTY_QUOTIENT = UInt64.valueOf(1 << 25); // 33_554_432
  UInt64 MIN_SLASHING_PENALTY_QUOTIENT = UInt64.valueOf(1 << 5); // 32

  /* Values defined in the spec. */

  default UInt64 getBaseRewardFactor() {
    return BASE_REWARD_FACTOR;
  }

  default UInt64 getWhistleblowerRewardQuotient() {
    return WHISTLEBLOWER_REWARD_QUOTIENT;
  }

  default UInt64 getProposerRewardQuotient() {
    return PROPOSER_REWARD_QUOTIENT;
  }

  default UInt64 getInactivityPenaltyQuotient() {
    return INACTIVITY_PENALTY_QUOTIENT;
  }

  default UInt64 getMinSlashingPenaltyQuotient() {
    return MIN_SLASHING_PENALTY_QUOTIENT;
  }
}
