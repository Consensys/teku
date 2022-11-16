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

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;

public interface BeaconStateEip4844 extends BeaconStateCapella {
  static BeaconStateEip4844 required(final BeaconState state) {
    return state
        .toVersionEip4844()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a EIP-4844 state but got: " + state.getClass().getSimpleName()));
  }

  static void describeCustomEip4844Fields(
      MoreObjects.ToStringHelper stringBuilder, BeaconStateCapella state) {
    BeaconStateCapella.describeCustomCapellaFields(stringBuilder, state);
    // no new fields
  }

  @Override
  MutableBeaconStateEip4844 createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateEip4844 updatedEip4844(
          final Mutator<MutableBeaconStateEip4844, E1, E2, E3> mutator) throws E1, E2, E3 {
    MutableBeaconStateEip4844 writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  default Optional<BeaconStateEip4844> toVersionEip4844() {
    return Optional.of(this);
  }
}
