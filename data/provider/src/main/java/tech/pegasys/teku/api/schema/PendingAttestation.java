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
import com.google.common.primitives.UnsignedLong;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;

public class PendingAttestation {
  public final Bitlist aggregation_bits;
  public final AttestationData data;
  public final UnsignedLong inclusion_delay;
  public final UnsignedLong proposer_index;

  @JsonCreator
  public PendingAttestation(
      @JsonProperty("aggregation_bits") final Bitlist aggregation_bits,
      @JsonProperty("data") final AttestationData data,
      @JsonProperty("inclusion_delay") final UnsignedLong inclusion_delay,
      @JsonProperty("proposer_index") final UnsignedLong proposer_index) {
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
}
