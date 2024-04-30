/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7594;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;

public interface BeaconStateEip7594 extends BeaconStateDeneb {
  static BeaconStateEip7594 required(final BeaconState state) {
    return state
        .toVersionEip7594()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an EIP7594 state but got: " + state.getClass().getSimpleName()));
  }

  static void describeCustomEip7594Fields(
      MoreObjects.ToStringHelper stringBuilder, BeaconStateDeneb state) {
    BeaconStateDeneb.describeCustomDenebFields(stringBuilder, state);
  }

  @Override
  MutableBeaconStateEip7594 createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateEip7594 updatedEip7594(
          final Mutator<MutableBeaconStateEip7594, E1, E2, E3> mutator) throws E1, E2, E3 {
    MutableBeaconStateEip7594 writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  default Optional<BeaconStateEip7594> toVersionEip7594() {
    return Optional.of(this);
  }
}
