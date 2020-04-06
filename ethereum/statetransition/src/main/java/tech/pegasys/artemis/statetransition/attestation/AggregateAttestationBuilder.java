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

package tech.pegasys.artemis.statetransition.attestation;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.bls.BLS;

class AggregateAttestationBuilder {
  private final Set<Attestation> includedAttestations = new HashSet<>();
  private final AttestationData attestationData;
  private Bitlist currentAggregateBits;

  AggregateAttestationBuilder(final AttestationData attestationData) {
    this.attestationData = attestationData;
  }

  public boolean canAggregate(final Attestation candidate) {
    return currentAggregateBits == null
        || !currentAggregateBits.intersects(candidate.getAggregation_bits());
  }

  public void aggregate(final Attestation attestation) {
    includedAttestations.add(attestation);
    if (currentAggregateBits == null) {
      currentAggregateBits = attestation.getAggregation_bits().copy();
    } else {
      currentAggregateBits.setAllBits(attestation.getAggregation_bits());
    }
  }

  public Attestation buildAggregate() {
    checkState(currentAggregateBits != null, "Must aggregate at least one attestation");
    return new Attestation(
        currentAggregateBits,
        attestationData,
        BLS.aggregate(includedAttestations.stream().map(Attestation::getAggregate_signature)));
  }

  public Collection<Attestation> getIncludedAttestations() {
    return includedAttestations;
  }
}
