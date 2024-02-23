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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;

public interface BeaconStateElectra extends BeaconStateDeneb {
  static BeaconStateElectra required(final BeaconState state) {
    return state
        .toVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an Electra state but got: " + state.getClass().getSimpleName()));
  }

  static void describeCustomElectraFields(
      MoreObjects.ToStringHelper stringBuilder, BeaconStateDeneb state) {
    BeaconStateDeneb.describeCustomDenebFields(stringBuilder, state);
    // no new fields
  }

  @Override
  MutableBeaconStateElectra createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateElectra updatedElectra(
          final Mutator<MutableBeaconStateElectra, E1, E2, E3> mutator) throws E1, E2, E3 {
    MutableBeaconStateElectra writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  default Optional<BeaconStateElectra> toVersionElectra() {
    return Optional.of(this);
  }
}
