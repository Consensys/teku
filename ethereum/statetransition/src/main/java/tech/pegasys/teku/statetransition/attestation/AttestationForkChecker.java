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

package tech.pegasys.teku.statetransition.attestation;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.teku.util.config.Constants.MIN_SEED_LOOKAHEAD;

import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AttestationForkChecker {

  private final Set<Bytes32> validRandaoMixes;

  public AttestationForkChecker(BeaconState currentState) {

    UInt64 currentStateEpoch = compute_epoch_at_slot(currentState.getSlot());
    UInt64 randaoIndexCurrentEpoch =
        currentStateEpoch.plus(EPOCHS_PER_HISTORICAL_VECTOR - MIN_SEED_LOOKAHEAD - 1);
    UInt64 randaoIndexPreviousEpoch =
        currentStateEpoch.plus(EPOCHS_PER_HISTORICAL_VECTOR - MIN_SEED_LOOKAHEAD - 2);

    Bytes32 randaoMixCurrentEpoch = get_randao_mix(currentState, randaoIndexCurrentEpoch);
    Bytes32 randaoMixPreviousEpoch = get_randao_mix(currentState, randaoIndexPreviousEpoch);
    this.validRandaoMixes = Set.of(randaoMixCurrentEpoch, randaoMixPreviousEpoch);
  }

  public boolean areAttestationsFromCorrectFork(
      final MatchingDataAttestationGroup attestationGroup) {
    return validRandaoMixes.contains(attestationGroup.getRandaoMix());
  }
}
