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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;

public interface BeaconStateCapella extends BeaconStateBellatrix {
  static BeaconStateCapella required(final BeaconState state) {
    return state
        .toVersionCapella()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a capella state but got: " + state.getClass().getSimpleName()));
  }

  static void describeCustomCapellaFields(
      MoreObjects.ToStringHelper stringBuilder, BeaconStateCapella state) {
    BeaconStateBellatrix.describeCustomBellatrixFields(stringBuilder, state);
  }

  @Override
  default Optional<BeaconStateCapella> toVersionCapella() {
    return Optional.of(this);
  }
}
