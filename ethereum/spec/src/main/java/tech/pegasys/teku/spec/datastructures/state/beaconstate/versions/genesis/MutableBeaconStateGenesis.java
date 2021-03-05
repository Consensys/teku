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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;

public interface MutableBeaconStateGenesis extends MutableBeaconState, BeaconStateGenesis {

  static MutableBeaconStateGenesis requireGenesisStateMutable(final MutableBeaconState state) {
    return state
        .toGenesisVersionMutable()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a genesis state but got: " + state.getClass().getSimpleName()));
  }

  // Attestations
  @Override
  default SSZMutableList<PendingAttestation> getPrevious_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class, getAnyByRef(15), Function.identity(), Function.identity());
  }

  @Override
  default SSZMutableList<PendingAttestation> getCurrent_epoch_attestations() {
    return new SSZBackingList<>(
        PendingAttestation.class, getAnyByRef(16), Function.identity(), Function.identity());
  }

  @Override
  BeaconStateGenesis commitChanges();

  @Override
  default Optional<MutableBeaconStateGenesis> toGenesisVersionMutable() {
    return Optional.of(this);
  }
}
