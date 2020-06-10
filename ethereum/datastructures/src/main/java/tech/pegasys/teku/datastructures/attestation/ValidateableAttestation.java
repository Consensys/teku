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

import com.google.common.base.Objects;
import com.google.common.base.Suppliers;
import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;

public class ValidateableAttestation {
  private final Attestation attestation;
  private final Optional<SignedAggregateAndProof> maybeAggregate;
  private final Supplier<Bytes32> hashTreeRoot;
  private final AtomicBoolean gossiped = new AtomicBoolean(false);

  private volatile Optional<IndexedAttestation> maybeIndexedAttestation = Optional.empty();

  public static ValidateableAttestation fromAttestation(Attestation attestation) {
    return new ValidateableAttestation(attestation, Optional.empty());
  }

  public static ValidateableAttestation fromSignedAggregate(SignedAggregateAndProof attestation) {
    return new ValidateableAttestation(
        attestation.getMessage().getAggregate(), Optional.of(attestation));
  }

  private ValidateableAttestation(
      Attestation attestation, Optional<SignedAggregateAndProof> aggregateAndProof) {
    this.maybeAggregate = aggregateAndProof;
    this.attestation = attestation;
    this.hashTreeRoot = Suppliers.memoize(attestation::hash_tree_root);
  }

  public IndexedAttestation getIndexedAttestation() {
    return maybeIndexedAttestation.orElseThrow(
        () ->
            new UnsupportedOperationException(
                "ValidateableAttestation does not have an IndexedAttestation yet."));
  }

  public void setIndexedAttestation(IndexedAttestation maybeIndexedAttestation) {
    this.maybeIndexedAttestation = Optional.of(maybeIndexedAttestation);
  }

  public boolean markGossiped() {
    return gossiped.compareAndSet(false, true);
  }

  public boolean isAggregate() {
    return maybeAggregate.isPresent();
  }

  public Attestation getAttestation() {
    return attestation;
  }

  public SignedAggregateAndProof getSignedAggregateAndProof() {
    return maybeAggregate.orElseThrow(
        () -> new UnsupportedOperationException("ValidateableAttestation is not an aggregate."));
  }

  public AttestationData getData() {
    return attestation.getData();
  }

  public UnsignedLong getEarliestSlotForForkChoiceProcessing() {
    return attestation.getEarliestSlotForForkChoiceProcessing();
  }

  public Collection<Bytes32> getDependentBlockRoots() {
    return attestation.getDependentBlockRoots();
  }

  public Bytes32 hash_tree_root() {
    return hashTreeRoot.get();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ValidateableAttestation)) return false;
    ValidateableAttestation that = (ValidateableAttestation) o;
    return Objects.equal(getAttestation(), that.getAttestation())
        && Objects.equal(maybeAggregate, that.maybeAggregate);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getAttestation(), maybeAggregate);
  }
}
