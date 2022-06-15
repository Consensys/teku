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
import java.util.List;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Eth1VotingSummarySchema {
  @JsonProperty("state_eth1_data")
  public Eth1Data stateEth1Data;

  @JsonProperty("eth1_data_votes")
  public List<Eth1DataWithVotesSchema> eth1DataVotes;

  @JsonProperty("votes_required")
  @Schema(type = "string", format = "uint64")
  public UInt64 votesRequired;

  @JsonProperty("voting_period_slots")
  @Schema(type = "string", format = "uint64")
  public UInt64 votingPeriodSlots;

  @JsonProperty("voting_period_slots_left")
  @Schema(type = "string", format = "uint64")
  public UInt64 votingPeriodSlotsLeft;

  @JsonCreator
  public Eth1VotingSummarySchema(
      @JsonProperty("state_eth1_data") Eth1Data stateEth1Data,
      @JsonProperty("eth1_data_votes") List<Eth1DataWithVotesSchema> eth1DataVotes,
      @JsonProperty("votes_required") UInt64 votesRequired,
      @JsonProperty("voting_period_slots") UInt64 votingPeriodSlots,
      @JsonProperty("voting_period_slots_left") UInt64 votingPeriodSlotsLeft) {
    this.stateEth1Data = stateEth1Data;
    this.eth1DataVotes = eth1DataVotes;
    this.votesRequired = votesRequired;
    this.votingPeriodSlots = votingPeriodSlots;
    this.votingPeriodSlotsLeft = votingPeriodSlotsLeft;
  }
}
