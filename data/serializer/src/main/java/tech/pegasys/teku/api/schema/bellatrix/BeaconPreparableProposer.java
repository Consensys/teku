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

package tech.pegasys.teku.api.schema.bellatrix;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_EXECUTION_ADDRESS;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_EXECUTION_ADDRESS;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_EXECUTION_ADDRESS;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.infrastructure.bytes.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@SuppressWarnings("JavaCase")
public class BeaconPreparableProposer {
  @JsonProperty("validator_index")
  @Schema(type = "string", format = "uint64", example = EXAMPLE_UINT64)
  public final UInt64 validator_index;

  @JsonProperty("fee_recipient")
  @Schema(
      type = "string",
      pattern = PATTERN_EXECUTION_ADDRESS,
      example = EXAMPLE_EXECUTION_ADDRESS,
      description = DESCRIPTION_EXECUTION_ADDRESS)
  public final Eth1Address fee_recipient;

  @JsonCreator
  public BeaconPreparableProposer(
      @JsonProperty("validator_index") UInt64 validator_index,
      @JsonProperty("fee_recipient") Eth1Address fee_recipient) {
    this.validator_index = validator_index;
    this.fee_recipient = fee_recipient;
  }
}
