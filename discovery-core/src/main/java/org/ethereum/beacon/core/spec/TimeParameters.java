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
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.SlotNumber.EpochLength;
import org.ethereum.beacon.core.types.Time;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Time parameters of the beacon chain.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#time-parameters">Time
 *     parameters</a> in the spec.
 */
public interface TimeParameters {

  Time SECONDS_PER_SLOT = Time.of(6); // 6 seconds
  SlotNumber MIN_ATTESTATION_INCLUSION_DELAY = SlotNumber.of(1); // 1 slot
  EpochLength SLOTS_PER_EPOCH = new EpochLength(UInt64.valueOf(1 << 6)); // 64 slots
  EpochNumber MIN_SEED_LOOKAHEAD = EpochNumber.of(1);
  EpochNumber ACTIVATION_EXIT_DELAY = EpochNumber.of(1 << 2);
  EpochNumber SLOTS_PER_ETH1_VOTING_PERIOD = EpochNumber.of(1 << 10); // 1024
  SlotNumber SLOTS_PER_HISTORICAL_ROOT = SlotNumber.of(1 << 13); // 8,192
  EpochNumber MIN_VALIDATOR_WITHDRAWABILITY_DELAY = EpochNumber.of(1 << 8);
  EpochNumber PERSISTENT_COMMITTEE_PERIOD = EpochNumber.of(1 << 11); // 2,048
  EpochNumber MAX_EPOCHS_PER_CROSSLINK = EpochNumber.of(1 << 6); // 64
  EpochNumber MIN_EPOCHS_TO_INACTIVITY_PENALTY = EpochNumber.of(1 << 2); // 4

  /* Values defined in the spec. */

  default Time getSecondsPerSlot() {
    return SECONDS_PER_SLOT;
  }

  default SlotNumber getMinAttestationInclusionDelay() {
    return MIN_ATTESTATION_INCLUSION_DELAY;
  }

  default EpochLength getSlotsPerEpoch() {
    return SLOTS_PER_EPOCH;
  }

  default EpochNumber getMinSeedLookahead() {
    return MIN_SEED_LOOKAHEAD;
  }

  default EpochNumber getActivationExitDelay() {
    return ACTIVATION_EXIT_DELAY;
  }

  default EpochNumber getSlotsPerEth1VotingPeriod() {
    return SLOTS_PER_ETH1_VOTING_PERIOD;
  }

  default SlotNumber getSlotsPerHistoricalRoot() {
    return SLOTS_PER_HISTORICAL_ROOT;
  }

  default EpochNumber getMinValidatorWithdrawabilityDelay() {
    return MIN_VALIDATOR_WITHDRAWABILITY_DELAY;
  }

  default EpochNumber getPersistentCommitteePeriod() {
    return PERSISTENT_COMMITTEE_PERIOD;
  }

  default EpochNumber getMaxEpochsPerCrosslink() {
    return MAX_EPOCHS_PER_CROSSLINK;
  }

  default EpochNumber getMinEpochsToInactivityPenalty() {
    return MIN_EPOCHS_TO_INACTIVITY_PENALTY;
  }
}
