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

package tech.pegasys.teku.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import io.swagger.v3.oas.annotations.media.Schema;

public class VoluntaryExit {
  @Schema(type = "string", format = "uint64")
  public final UnsignedLong epoch;

  @Schema(type = "string", format = "uint64")
  public final UnsignedLong validator_index;

  public VoluntaryExit(tech.pegasys.teku.datastructures.operations.VoluntaryExit voluntaryExit) {
    this.epoch = voluntaryExit.getEpoch();
    this.validator_index = voluntaryExit.getValidator_index();
  }

  @JsonCreator
  public VoluntaryExit(
      @JsonProperty("epoch") final UnsignedLong epoch,
      @JsonProperty("validator_index") final UnsignedLong validator_index) {
    this.epoch = epoch;
    this.validator_index = validator_index;
  }

  public tech.pegasys.teku.datastructures.operations.VoluntaryExit asInternalVoluntaryExit() {
    return new tech.pegasys.teku.datastructures.operations.VoluntaryExit(epoch, validator_index);
  }
}
