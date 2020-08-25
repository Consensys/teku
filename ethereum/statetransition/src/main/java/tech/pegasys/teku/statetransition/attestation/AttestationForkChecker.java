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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_seed;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_BEACON_ATTESTER;

import java.util.HashSet;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AttestationForkChecker {

  private final Set<Bytes32> validCommitteeShufflingSeeds = new HashSet<>();

  public AttestationForkChecker(BeaconState state) {

    UInt64 epoch = compute_epoch_at_slot(state.getSlot());
    Bytes32 currentEpochSeed = get_seed(state, epoch, DOMAIN_BEACON_ATTESTER);
    validCommitteeShufflingSeeds.add(currentEpochSeed);

    if (!epoch.equals(UInt64.ZERO)) {
      Bytes32 previousEpochSeed = get_seed(state, epoch.minus(UInt64.ONE), DOMAIN_BEACON_ATTESTER);
      validCommitteeShufflingSeeds.add(previousEpochSeed);
    }
  }

  public boolean areAttestationsFromCorrectFork(
      final MatchingDataAttestationGroup attestationGroup) {
    return validCommitteeShufflingSeeds.contains(attestationGroup.getCommitteeShufflingSeed());
  }
}
