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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES4;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class Fork {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES4)
  public Bytes4 previous_version;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES4)
  public Bytes4 current_version;

  @Schema(type = "string", format = "uint64")
  public UnsignedLong epoch;

  @JsonCreator
  public Fork(
      @JsonProperty("previous_version") final Bytes4 previous_version,
      @JsonProperty("current_version") final Bytes4 current_version,
      @JsonProperty("epoch") final UnsignedLong epoch) {
    this.previous_version = previous_version;
    this.current_version = current_version;
    this.epoch = epoch;
  }

  public Fork(final tech.pegasys.teku.datastructures.state.Fork fork) {
    this.previous_version = fork.getPrevious_version();
    this.current_version = fork.getCurrent_version();
    this.epoch = fork.getEpoch();
  }
}
