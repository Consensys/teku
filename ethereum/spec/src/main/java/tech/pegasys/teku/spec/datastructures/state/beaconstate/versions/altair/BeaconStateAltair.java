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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;

public interface BeaconStateAltair extends BeaconState {

  static BeaconStateAltair required(final BeaconState state) {
    return state
        .toVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an altair state but got: " + state.getClass().getSimpleName()));
  }

  // Participation
  default SszList<SszByte> getPreviousEpochParticipation() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION.name());
    return getAny(fieldIndex);
  }

  default SszList<SszByte> getCurrentEpochParticipation() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION.name());
    return getAny(fieldIndex);
  }

  @Override
  default Optional<BeaconStateAltair> toVersionAltair() {
    return Optional.of(this);
  }

  <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateAltair updatedAltair(Mutator<MutableBeaconStateAltair, E1, E2, E3> mutator)
          throws E1, E2, E3;
}
