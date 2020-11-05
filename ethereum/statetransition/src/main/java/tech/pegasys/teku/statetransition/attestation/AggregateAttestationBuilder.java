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

package tech.pegasys.teku.statetransition.attestation;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;

/**
 * Builds an aggregate attestation, providing functions to test if an attestation can be added or is
 * made redundant by the current aggregate.
 */
class AggregateAttestationBuilder {
  private final Set<ValidateableAttestation> includedAttestations = new HashSet<>();
  private final AttestationData attestationData;
  private Bitlist currentAggregateBits;

  AggregateAttestationBuilder(final AttestationData attestationData) {
    this.attestationData = attestationData;
  }

  public boolean canAggregate(final ValidateableAttestation candidate) {
    return currentAggregateBits == null
        || !currentAggregateBits.intersects(candidate.getAttestation().getAggregation_bits());
  }

  public boolean isFullyIncluded(final ValidateableAttestation candidate) {
    return currentAggregateBits != null
        && currentAggregateBits.isSuperSetOf(candidate.getAttestation().getAggregation_bits());
  }

  public void aggregate(final ValidateableAttestation attestation) {
    includedAttestations.add(attestation);
    if (currentAggregateBits == null) {
      currentAggregateBits = attestation.getAttestation().getAggregation_bits().copy();
    } else {
      currentAggregateBits.setAllBits(attestation.getAttestation().getAggregation_bits());
    }
  }

  public ValidateableAttestation buildAggregate() {
    checkState(currentAggregateBits != null, "Must aggregate at least one attestation");
    return ValidateableAttestation.from(
        new Attestation(
            currentAggregateBits,
            attestationData,
            BLS.aggregate(
                includedAttestations.stream()
                    .map(ValidateableAttestation::getAttestation)
                    .map(Attestation::getAggregate_signature)
                    .collect(Collectors.toList()))));
  }

  public Collection<ValidateableAttestation> getIncludedAttestations() {
    return includedAttestations;
  }
}
