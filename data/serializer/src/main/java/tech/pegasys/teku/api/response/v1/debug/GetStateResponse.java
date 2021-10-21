/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api.response.v1.debug;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.api.schema.phase0.BeaconStatePhase0;

public class GetStateResponse {
  public final BeaconStatePhase0 data;

  @JsonCreator
  public GetStateResponse(@JsonProperty("data") final BeaconStatePhase0 data) {
    this.data = data;
  }

  public GetStateResponse(final Version version, final BeaconState data) {
    if (!version.equals(Version.phase0)) {
      throw new IllegalArgumentException(
          String.format("Beacon state at slot %s is not a phase0 state", data.slot));
    }
    this.data = (BeaconStatePhase0) data;
  }
}
