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

package tech.pegasys.teku.api.schema.merge;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES20;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_BYTES20;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconPreparableProposer {
  @JsonProperty("validator_index")
  @Schema(type = "string", format = "uint64", example = EXAMPLE_UINT64)
  public final UInt64 validator_index;

  @JsonProperty("fee_recipient")
  @Schema(
      type = "string",
      format = "byte",
      pattern = PATTERN_BYTES20,
      description = DESCRIPTION_BYTES20)
  public final Bytes20 fee_recipient;

  @JsonCreator
  public BeaconPreparableProposer(
      @JsonProperty("validator_index") UInt64 validator_index,
      @JsonProperty("fee_recipient") Bytes20 fee_recipient) {
    this.validator_index = validator_index;
    this.fee_recipient = fee_recipient;
  }

  public static tech.pegasys.teku.spec.datastructures.operations.versions.merge
          .BeaconPreparableProposer
      asInternalBeaconPreparableProposer(BeaconPreparableProposer beaconPreparableProposer) {
    return new tech.pegasys.teku.spec.datastructures.operations.versions.merge
        .BeaconPreparableProposer(
        beaconPreparableProposer.validator_index, beaconPreparableProposer.fee_recipient);
  }
}
