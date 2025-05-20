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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBitsAggregator;

/**
 * Builds an aggregate attestation, providing functions to test if an attestation can be added or is
 * made redundant by the current aggregate.
 */
class AggregateAttestationBuilder {
  private final List<PooledAttestation> includedAttestations = new ArrayList<>();
  private AttestationBitsAggregator currentAggregateBits;

  public boolean aggregate(final PooledAttestation attestation) {

    if (currentAggregateBits == null) {
      includedAttestations.add(attestation);
      currentAggregateBits = attestation.bits().copy();
      return true;
    }
    if (currentAggregateBits.aggregateWith(attestation.bits())) {
      includedAttestations.add(attestation);
      return true;
    }
    return false;
  }

  public PooledAttestation buildAggregate() {
    checkState(currentAggregateBits != null, "Must aggregate at least one attestation");
    return new PooledAttestation(
        currentAggregateBits,
        BLS.aggregate(
            includedAttestations.stream().map(PooledAttestation::aggregatedSignature).toList()),
        false);
  }

  public Collection<PooledAttestation> getIncludedAttestations() {
    return includedAttestations;
  }
}
