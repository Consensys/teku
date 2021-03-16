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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;

public interface MutableBeaconStateAltair extends MutableBeaconState, BeaconStateAltair {

  static MutableBeaconStateAltair required(final MutableBeaconState state) {
    return state
        .toMutableVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a altair state but got: " + state.getClass().getSimpleName()));
  }

  // Participation
  @Override
  default SszMutableList<SszByte> getPreviousEpochParticipation() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION.name());
    return getAnyByRef(fieldIndex);
  }

  @Override
  default SszMutableList<SszByte> getCurrentEpochParticipation() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION.name());
    return getAnyByRef(fieldIndex);
  }

  @Override
  BeaconStateAltair commitChanges();

  @Override
  default Optional<MutableBeaconStateAltair> toMutableVersionAltair() {
    return Optional.of(this);
  }
}
