/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip4844;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.MutableBeaconStateCapella;

public interface MutableBeaconStateEip4844 extends MutableBeaconStateCapella, BeaconStateEip4844 {
  static MutableBeaconStateEip4844 required(final MutableBeaconState state) {
    return state
        .toMutableVersionEip4844()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a EIP-4844 state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  BeaconStateEip4844 commitChanges();

  @Override
  default Optional<MutableBeaconStateEip4844> toMutableVersionEip4844() {
    return Optional.of(this);
  }
}
