/*
 * Copyright Consensys Software Inc., 2025
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
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBitsAggregator;

/**
 * Builds an aggregate attestation, providing functions to test if an attestation can be added or is
 * made redundant by the current aggregate.
 */
class AggregateAttestationBuilder {
  private final Spec spec;
  private final Set<ValidatableAttestation> includedAttestations = new HashSet<>();
  private final AttestationData attestationData;
  private AttestationBitsAggregator currentAggregateBits;

  AggregateAttestationBuilder(final Spec spec, final AttestationData attestationData) {
    this.spec = spec;
    this.attestationData = attestationData;
  }

  public boolean isFullyIncluded(final ValidatableAttestation candidate) {
    return currentAggregateBits != null
        && currentAggregateBits.isSuperSetOf(candidate.getAttestation());
  }

  public boolean aggregate(final ValidatableAttestation attestation) {

    if (currentAggregateBits == null) {
      includedAttestations.add(attestation);
      currentAggregateBits = AttestationBitsAggregator.of(attestation);
      return true;
    }
    if (currentAggregateBits.aggregateWith(attestation.getAttestation())) {
      includedAttestations.add(attestation);
      return true;
    }
    return false;
  }

  public ValidatableAttestation buildAggregate() {
    checkState(currentAggregateBits != null, "Must aggregate at least one attestation");
    final SpecVersion specVersion = spec.atSlot(attestationData.getSlot());
    return ValidatableAttestation.from(
        spec,
        specVersion
            .getSchemaDefinitions()
            .getAttestationSchema()
            .create(
                currentAggregateBits.getAggregationBits(),
                attestationData,
                BLS.aggregate(
                    includedAttestations.stream()
                        .map(ValidatableAttestation::getAttestation)
                        .map(Attestation::getAggregateSignature)
                        .toList()),
                currentAggregateBits::getCommitteeBits));
  }

  public Collection<ValidatableAttestation> getIncludedAttestations() {
    return includedAttestations;
  }
}
