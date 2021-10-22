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

package tech.pegasys.teku.api.schema.altair;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SyncCommitteeSubnetSubscription {
  @JsonProperty("validator_index")
  @Schema(type = "string", format = "uint64")
  public final UInt64 validatorIndex;

  @JsonProperty("sync_committee_indices")
  @ArraySchema(schema = @Schema(type = "string", format = "uint64"))
  public final List<UInt64> syncCommitteeIndices;

  @JsonProperty("until_epoch")
  @Schema(
      type = "string",
      format = "uint64",
      description =
          "The final epoch (exclusive value) that the specified validator requires the subscription for.")
  public final UInt64 untilEpoch;

  @JsonCreator
  public SyncCommitteeSubnetSubscription(
      @JsonProperty("validator_index") final UInt64 validatorIndex,
      @JsonProperty("sync_committee_indices") final List<UInt64> syncCommitteeIndices,
      @JsonProperty("until_epoch") final UInt64 untilEpoch) {
    this.validatorIndex = validatorIndex;
    this.syncCommitteeIndices = syncCommitteeIndices;
    this.untilEpoch = untilEpoch;
  }
}
