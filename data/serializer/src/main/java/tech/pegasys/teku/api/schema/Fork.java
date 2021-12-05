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
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_BYTES4;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Fork {
  @Schema(type = "string", pattern = PATTERN_BYTES4, description = DESCRIPTION_BYTES4)
  public Bytes4 previous_version;

  @Schema(type = "string", pattern = PATTERN_BYTES4, description = DESCRIPTION_BYTES4)
  public Bytes4 current_version;

  @Schema(type = "string")
  public UInt64 epoch;

  @JsonCreator
  public Fork(
      @JsonProperty("previous_version") final Bytes4 previous_version,
      @JsonProperty("current_version") final Bytes4 current_version,
      @JsonProperty("epoch") final UInt64 epoch) {
    this.previous_version = previous_version;
    this.current_version = current_version;
    this.epoch = epoch;
  }

  public Fork(final tech.pegasys.teku.spec.datastructures.state.Fork fork) {
    this.previous_version = fork.getPrevious_version();
    this.current_version = fork.getCurrent_version();
    this.epoch = fork.getEpoch();
  }

  public tech.pegasys.teku.spec.datastructures.state.Fork asInternalFork() {
    return new tech.pegasys.teku.spec.datastructures.state.Fork(
        previous_version, current_version, epoch);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Fork fork = (Fork) o;
    return Objects.equals(previous_version, fork.previous_version)
        && Objects.equals(current_version, fork.current_version)
        && Objects.equals(epoch, fork.epoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(previous_version, current_version, epoch);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("previous_version", previous_version)
        .add("current_version", current_version)
        .add("epoch", epoch)
        .toString();
  }
}
