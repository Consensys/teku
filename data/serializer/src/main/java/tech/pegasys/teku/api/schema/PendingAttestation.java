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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES_SSZ;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;

public class PendingAttestation {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES_SSZ)
  public final Bitlist aggregation_bits;

  public final AttestationData data;

  @Schema(type = "string", format = "uint64")
  public final UInt64 inclusion_delay;

  @Schema(type = "string", format = "uint64")
  public final UInt64 proposer_index;

  @JsonCreator
  public PendingAttestation(
      @JsonProperty("aggregation_bits") final Bitlist aggregation_bits,
      @JsonProperty("data") final AttestationData data,
      @JsonProperty("inclusion_delay") final UInt64 inclusion_delay,
      @JsonProperty("proposer_index") final UInt64 proposer_index) {
    this.aggregation_bits = aggregation_bits;
    this.data = data;
    this.inclusion_delay = inclusion_delay;
    this.proposer_index = proposer_index;
  }

  public PendingAttestation(
      final tech.pegasys.teku.datastructures.state.PendingAttestation pendingAttestation) {
    this.aggregation_bits = pendingAttestation.getAggregation_bits();
    this.data = new AttestationData(pendingAttestation.getData());
    this.inclusion_delay = pendingAttestation.getInclusion_delay();
    this.proposer_index = pendingAttestation.getProposer_index();
  }

  public tech.pegasys.teku.datastructures.state.PendingAttestation asInternalPendingAttestation() {
    return new tech.pegasys.teku.datastructures.state.PendingAttestation(
        aggregation_bits, data.asInternalAttestationData(), inclusion_delay, proposer_index);
  }
}
