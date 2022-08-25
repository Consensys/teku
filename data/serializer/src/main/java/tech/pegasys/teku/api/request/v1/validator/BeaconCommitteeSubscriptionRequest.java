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

package tech.pegasys.teku.api.request.v1.validator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@SuppressWarnings("JavaCase")
public class BeaconCommitteeSubscriptionRequest {

  @Schema(type = "string")
  public final String validator_index;

  @Schema(type = "string")
  public final String committee_index;

  @Schema(type = "string", format = "uint64")
  public final UInt64 committees_at_slot;

  @Schema(type = "string", format = "uint64")
  public final UInt64 slot;

  public final boolean is_aggregator;

  @JsonCreator
  public BeaconCommitteeSubscriptionRequest(
      @JsonProperty("validator_index") final String validator_index,
      @JsonProperty("committee_index") final String committee_index,
      @JsonProperty("committees_at_slot") final UInt64 committees_at_slot,
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("is_aggregator") final boolean is_aggregator) {
    this.committee_index = committee_index;
    this.validator_index = validator_index;
    this.committees_at_slot = committees_at_slot;
    this.slot = slot;
    this.is_aggregator = is_aggregator;
  }
}
