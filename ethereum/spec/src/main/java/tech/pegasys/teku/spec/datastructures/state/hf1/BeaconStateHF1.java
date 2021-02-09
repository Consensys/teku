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

package tech.pegasys.teku.spec.datastructures.state.hf1;

import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives;

public interface BeaconStateHF1 extends BeaconState {

  SSZList<SSZVector<SszPrimitives.SszBit>> getPreviousEpochParticipation();

  SSZList<SSZVector<SszPrimitives.SszBit>> getCurrentEpochParticipation();

  BeaconStateHF1 updatedHF1(final Consumer<MutableBeaconStateHF1> updater);

  @Override
  default Optional<BeaconStateHF1> toHF1Version() {
    return Optional.of(this);
  }
}
