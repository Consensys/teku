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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis;

import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public interface BeaconStateGenesis extends BeaconState {

  static BeaconStateGenesis requireGenesisState(final BeaconState state) {
    return state
        .toGenesisVersion()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a genesis state but got: " + state.getClass().getSimpleName()));
  }

  // Attestations
  default SSZList<PendingAttestation> getPrevious_epoch_attestations() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name());
    return new SSZBackingList<>(
        PendingAttestation.class, getAny(fieldIndex), Function.identity(), Function.identity());
  }

  default SSZList<PendingAttestation> getCurrent_epoch_attestations() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name());
    return new SSZBackingList<>(
        PendingAttestation.class, getAny(fieldIndex), Function.identity(), Function.identity());
  }

  @Override
  default Optional<BeaconStateGenesis> toGenesisVersion() {
    return Optional.of(this);
  }

  <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateGenesis updatedGenesis(Mutator<MutableBeaconStateGenesis, E1, E2, E3> mutator)
          throws E1, E2, E3;
}
