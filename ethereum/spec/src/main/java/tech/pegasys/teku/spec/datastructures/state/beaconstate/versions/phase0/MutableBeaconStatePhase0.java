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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableList;

public interface MutableBeaconStatePhase0 extends MutableBeaconState, BeaconStatePhase0 {

  static MutableBeaconStatePhase0 required(final MutableBeaconState state) {
    return state
        .toMutableVersionPhase0()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a phase0 state but got: " + state.getClass().getSimpleName()));
  }

  // Attestations
  @Override
  default SszMutableList<PendingAttestation> getPrevious_epoch_attestations() {
    return getAnyByRef(15);
  }

  default void setPrevious_epoch_attestations(
      SszList<PendingAttestation> previous_epoch_attestations) {
    set(15, previous_epoch_attestations);
  }

  @Override
  default SszMutableList<PendingAttestation> getCurrent_epoch_attestations() {
    return getAnyByRef(16);
  }

  default void setCurrent_epoch_attestations(
      SszList<PendingAttestation> current_epoch_attestations) {
    set(16, current_epoch_attestations);
  }

  @Override
  BeaconStatePhase0 commitChanges();

  @Override
  default Optional<MutableBeaconStatePhase0> toMutableVersionPhase0() {
    return Optional.of(this);
  }
}
