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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;

class AggregateAttestationBuilder {
  private final Set<Attestation> includedAttestations = new HashSet<>();
  private Attestation currentAggregate;

  public boolean canAggregate(final Attestation candidate) {
    return currentAggregate == null
        || !currentAggregate.getAggregation_bits().intersects(candidate.getAggregation_bits());
  }

  public void aggregate(final Attestation attestation) {
    includedAttestations.add(attestation);
    if (currentAggregate == null) {
      currentAggregate = attestation;
    } else {
      currentAggregate = AttestationUtil.aggregateAttestations(currentAggregate, attestation);
    }
  }

  public Attestation getAggregate() {
    return currentAggregate;
  }

  public Collection<Attestation> getIncludedAttestations() {
    return includedAttestations;
  }
}
