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

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.MutableBeaconStateDeneb;

public interface MutableBeaconStateEip7594 extends MutableBeaconStateDeneb, BeaconStateEip7594 {
  static MutableBeaconStateEip7594 required(final MutableBeaconState state) {
    return state
        .toMutableVersionEip7594()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an EIP7594 state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  BeaconStateEip7594 commitChanges();

  @Override
  default Optional<MutableBeaconStateEip7594> toMutableVersionEip7594() {
    return Optional.of(this);
  }
}
