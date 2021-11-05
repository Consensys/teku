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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;

public interface MutableBeaconStateMerge extends MutableBeaconStateAltair, BeaconStateMerge {

  static MutableBeaconStateMerge required(final MutableBeaconState state) {
    return state
        .toMutableVersionMerge()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a merge state but got: " + state.getClass().getSimpleName()));
  }

  default void setLatestExecutionPayloadHeader(ExecutionPayloadHeader executionPayloadHeader) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER.name());
    set(fieldIndex, executionPayloadHeader);
  }

  @Override
  BeaconStateMerge commitChanges();

  @Override
  default Optional<MutableBeaconStateMerge> toMutableVersionMerge() {
    return Optional.of(this);
  }

  @Override
  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateMerge updatedMerge(Mutator<MutableBeaconStateMerge, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    throw new UnsupportedOperationException();
  }
}
