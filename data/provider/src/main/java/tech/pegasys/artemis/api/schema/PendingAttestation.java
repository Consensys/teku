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

package tech.pegasys.artemis.api.schema;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;

public class PendingAttestation {
  public final Bitlist aggregation_bits;
  public final AttestationData data;
  public final UnsignedLong inclusion_delay;
  public final UnsignedLong proposer_index;

  public PendingAttestation(
      tech.pegasys.artemis.datastructures.state.PendingAttestation pendingAttestation) {
    this.aggregation_bits = pendingAttestation.getAggregation_bits();
    this.data = new AttestationData(pendingAttestation.getData());
    this.inclusion_delay = pendingAttestation.getInclusion_delay();
    this.proposer_index = pendingAttestation.getProposer_index();
  }
}
