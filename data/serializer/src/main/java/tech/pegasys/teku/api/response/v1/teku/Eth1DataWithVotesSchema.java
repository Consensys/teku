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

package tech.pegasys.teku.api.response.v1.teku;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Eth1DataWithVotesSchema {
  @JsonProperty("eth1_data")
  public Eth1Data eth1Data;

  @JsonProperty("votes")
  @Schema(type = "string", format = "uint64")
  public UInt64 votes;

  @JsonCreator
  public Eth1DataWithVotesSchema(
      @JsonProperty("eth1_data") final Eth1Data eth1Data,
      @JsonProperty("votes") final UInt64 votes) {
    this.eth1Data = eth1Data;
    this.votes = votes;
  }
}
