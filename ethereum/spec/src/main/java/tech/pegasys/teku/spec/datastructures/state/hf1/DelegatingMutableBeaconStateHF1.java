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

import java.util.function.Consumer;
import tech.pegasys.teku.spec.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.genesis.DelegatingMutableBeaconStateGenesis;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives;

public class DelegatingMutableBeaconStateHF1 extends DelegatingMutableBeaconStateGenesis
    implements MutableBeaconStateHF1 {
  public DelegatingMutableBeaconStateHF1(final MutableBeaconState state) {
    super(state);
  }

  @Override
  public void updatePreviousEpochParticipation(
      final Consumer<SSZMutableList<SSZVector<SszPrimitives.SszBit>>> updater) {
    state.maybeUpdatePreviousEpochParticipation(
        maybeValue -> updater.accept(maybeValue.orElseThrow()));
  }

  @Override
  public void updateCurrentEpochParticipation(
      final Consumer<SSZMutableList<SSZVector<SszPrimitives.SszBit>>> updater) {
    state.maybeUpdateCurrentEpochParticipation(
        maybeValue -> updater.accept(maybeValue.orElseThrow()));
  }
}
