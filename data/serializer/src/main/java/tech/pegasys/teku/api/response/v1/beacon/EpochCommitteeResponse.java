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

package tech.pegasys.teku.api.response.v1.beacon;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64Util;

public class EpochCommitteeResponse {
  @JsonProperty("index")
  @Schema(type = "string", example = EXAMPLE_UINT64, description = "Index of committee")
  public final UInt64 index;

  @JsonProperty("slot")
  @Schema(
      type = "string",
      example = EXAMPLE_UINT64,
      description = "The slot at which the committee has to attest.")
  public final UInt64 slot;

  @JsonProperty("validators")
  @ArraySchema(schema = @Schema(type = "string", example = EXAMPLE_UINT64))
  public final List<UInt64> validators;

  public EpochCommitteeResponse(
      tech.pegasys.teku.datastructures.state.CommitteeAssignment committeeAssignment) {
    this.slot = committeeAssignment.getSlot();
    this.index = committeeAssignment.getCommitteeIndex();
    this.validators = UInt64Util.intToUInt64List(committeeAssignment.getCommittee());
  }

  @JsonCreator
  public EpochCommitteeResponse(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("index") final UInt64 index,
      @JsonProperty("validators") final List<UInt64> validators) {
    this.slot = slot;
    this.index = index;
    this.validators = validators;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EpochCommitteeResponse)) return false;
    EpochCommitteeResponse that = (EpochCommitteeResponse) o;
    return Objects.equal(index, that.index)
        && Objects.equal(slot, that.slot)
        && Objects.equal(validators, that.validators);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(index, slot, validators);
  }
}
