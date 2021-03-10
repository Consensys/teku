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
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;

public interface MutableBeaconStateAltair extends MutableBeaconState, BeaconStateAltair {

  static MutableBeaconStateAltair required(final MutableBeaconState state) {
    return state
        .toMutableVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a altair state but got: " + state.getClass().getSimpleName()));
  }

  // Attestations
  @Override
  default SSZMutableList<Bytes32> getHistorical_roots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS.name());
    return new SSZBackingList<>(
        Bytes32.class, getAnyByRef(fieldIndex), SszBytes32::new, AbstractSszPrimitive::get);
  }

  @Override
  default SSZMutableList<SszBitvector> getPreviousEpochParticipation() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION.name());
    return new SSZBackingList<>(
        SszBitvector.class, getAnyByRef(fieldIndex), Function.identity(), Function.identity());
  }

  @Override
  default SSZMutableList<SszBitvector> getCurrentEpochParticipation() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION.name());
    return new SSZBackingList<>(
        SszBitvector.class, getAnyByRef(fieldIndex), Function.identity(), Function.identity());
  }

  @Override
  BeaconStateAltair commitChanges();

  @Override
  default Optional<MutableBeaconStateAltair> toMutableVersionAltair() {
    return Optional.of(this);
  }
}
