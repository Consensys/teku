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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;

public interface BeaconStateBellatrix extends BeaconStateAltair {

  static BeaconStateBellatrix required(final BeaconState state) {
    return state
        .toVersionBellatrix()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a bellatrix state but got: " + state.getClass().getSimpleName()));
  }

  default ExecutionPayloadHeader getLatestExecutionPayloadHeader() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER);
    return getAny(fieldIndex);
  }

  @Override
  default Optional<BeaconStateBellatrix> toVersionBellatrix() {
    return Optional.of(this);
  }

  @Override
  MutableBeaconStateBellatrix createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateBellatrix updatedBellatrix(
          Mutator<MutableBeaconStateBellatrix, E1, E2, E3> mutator) throws E1, E2, E3 {
    MutableBeaconStateBellatrix writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  static void describeCustomBellatrixFields(
      MoreObjects.ToStringHelper stringBuilder, final BeaconStateBellatrix state) {
    stringBuilder.add("execution_payload_header", state.getLatestExecutionPayloadHeader());
  }
}
