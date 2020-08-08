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
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Committee {
  @Schema(type = "string", format = "uint64")
  public final UInt64 slot;

  @Schema(type = "string", format = "uint64")
  public final UInt64 index;

  public final List<Integer> committee;

  public Committee(tech.pegasys.teku.datastructures.state.CommitteeAssignment committeeAssignment) {
    this.slot = committeeAssignment.getSlot();
    this.index = committeeAssignment.getCommitteeIndex();
    this.committee = committeeAssignment.getCommittee();
  }

  @JsonCreator
  public Committee(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("index") final UInt64 index,
      @JsonProperty("committee") final List<Integer> committee) {
    this.slot = slot;
    this.index = index;
    this.committee = committee;
  }
}
