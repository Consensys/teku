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

package tech.pegasys.teku.statetransition.attestation;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;

/**
 * Builds an aggregate attestation, providing functions to test if an attestation can be added or is
 * made redundant by the current aggregate.
 */
class AggregateAttestationBuilder {
  private final Spec spec;
  private final Set<ValidateableAttestation> includedAttestations = new HashSet<>();
  private final AttestationData attestationData;
  private SszBitlist currentAggregateBits;

  AggregateAttestationBuilder(final Spec spec, final AttestationData attestationData) {
    this.spec = spec;
    this.attestationData = attestationData;
  }

  public boolean canAggregate(final ValidateableAttestation candidate) {
    return currentAggregateBits == null
        || !currentAggregateBits.intersects(candidate.getAttestation().getAggregationBits());
  }

  public boolean isFullyIncluded(final ValidateableAttestation candidate) {
    return currentAggregateBits != null
        && currentAggregateBits.isSuperSetOf(candidate.getAttestation().getAggregationBits());
  }

  public void aggregate(final ValidateableAttestation attestation) {
    includedAttestations.add(attestation);
    if (currentAggregateBits == null) {
      currentAggregateBits = attestation.getAttestation().getAggregationBits();
    } else {
      currentAggregateBits =
          currentAggregateBits.or(attestation.getAttestation().getAggregationBits());
    }
  }

  public ValidateableAttestation buildAggregate() {
    checkState(currentAggregateBits != null, "Must aggregate at least one attestation");
    return ValidateableAttestation.from(
        spec,
        spec.atSlot(attestationData.getSlot())
            .getSchemaDefinitions()
            .getAttestationSchema()
            .create(
                currentAggregateBits,
                attestationData,
                BLS.aggregate(
                    includedAttestations.stream()
                        .map(ValidateableAttestation::getAttestation)
                        .map(Attestation::getAggregateSignature)
                        .collect(Collectors.toList()))));
  }

  public Collection<ValidateableAttestation> getIncludedAttestations() {
    return Collections.unmodifiableCollection(includedAttestations);
  }
}
