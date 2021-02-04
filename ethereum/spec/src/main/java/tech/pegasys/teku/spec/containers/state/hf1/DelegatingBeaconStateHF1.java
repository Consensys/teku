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

package tech.pegasys.teku.spec.containers.state.hf1;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Consumer;
import tech.pegasys.teku.spec.containers.state.BeaconState;
import tech.pegasys.teku.spec.containers.state.DelegatingBeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives;

public class DelegatingBeaconStateHF1 extends DelegatingBeaconState implements BeaconStateHF1 {

  public DelegatingBeaconStateHF1(final BeaconState state) {
    super(state);
    validate();
  }

  private void validate() {
    checkArgument(
        state.maybeGetPreviousEpochParticipation().isPresent(),
        "Field previousEpochParticipation is required");
    checkArgument(
        state.maybeGetCurrentEpochParticipation().isPresent(),
        "Field currentEpochParticipation is required");
  }

  @Override
  public SSZList<SSZVector<SszPrimitives.SszBit>> getPreviousEpochParticipation() {
    return state.maybeGetPreviousEpochParticipation().orElseThrow();
  }

  @Override
  public SSZList<SSZVector<SszPrimitives.SszBit>> getCurrentEpochParticipation() {
    return state.maybeGetCurrentEpochParticipation().orElseThrow();
  }

  @Override
  public BeaconState updateHF1(final Consumer<MutableBeaconStateHF1> updater) {
    return state.update(
        mutable -> {
          final DelegatingMutableBeaconStateHF1 mutableHF1 =
              new DelegatingMutableBeaconStateHF1(mutable);
          updater.accept(mutableHF1);
        });
  }
}
