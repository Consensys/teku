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

import java.util.Objects;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBitsAggregator;

public record AttestationBitsAndSignature(
    AttestationBitsAggregator bits, BLSSignature aggregatedSignature, boolean isSingleAttestation) {

  public static AttestationBitsAndSignature fromValidatableAttestation(
      final ValidatableAttestation attestation) {
    return new AttestationBitsAndSignature(
        AttestationBitsAggregator.of(attestation),
        attestation.getAttestation().getAggregateSignature(),
        attestation.getUnconvertedAttestation().isSingleAttestation());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AttestationBitsAndSignature that)) {
      return false;
    }

    // we intentionally ignore isSingleAttestation in equals/hashCode
    // if bits and signature are equal, the originating attestation type doesn't matter
    // considering it creates some problems in tests
    return Objects.equals(aggregatedSignature, that.aggregatedSignature)
        && Objects.equals(bits, that.bits);
  }

  @Override
  public int hashCode() {
    return Objects.hash(aggregatedSignature, bits);
  }
}
