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

package tech.pegasys.teku.datastructures.attestation;

import java.util.Optional;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;

public class ValidateableAttestation {
  private final Attestation attestation;
  private final Optional<SignedAggregateAndProof> maybeAggregate;

  public static ValidateableAttestation fromSingle(Attestation attestation) {
    return new ValidateableAttestation(attestation, Optional.empty());
  }

  public static ValidateableAttestation fromAggregate(SignedAggregateAndProof attestation) {
    return new ValidateableAttestation(attestation.getMessage().getAggregate(), Optional.of(attestation));
  }

  private ValidateableAttestation(Attestation attestation,
                                  Optional<SignedAggregateAndProof> aggregateAndProof) {
    maybeAggregate = aggregateAndProof;
    this.attestation = attestation;
  }

  public boolean isAggregate() {
    return maybeAggregate.isPresent();
  }

  public Attestation getAttestation() {
    return attestation;
  }

  public SignedAggregateAndProof getSignedAggregateAndProof() {
    if (!isAggregate()) {
      throw new UnsupportedOperationException("ValidateableAttestation is not an aggregate.");
    }
    return maybeAggregate.get();
  }
}
