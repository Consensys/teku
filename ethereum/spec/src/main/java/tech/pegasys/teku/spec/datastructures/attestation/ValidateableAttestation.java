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

package tech.pegasys.teku.spec.datastructures.attestation;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Suppliers;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ValidateableAttestation {
  private final Spec spec;
  private final Attestation attestation;
  private final Optional<SignedAggregateAndProof> maybeAggregate;
  private final Supplier<Bytes32> hashTreeRoot;
  private final AtomicBoolean gossiped = new AtomicBoolean(false);
  private final boolean producedLocally;

  private volatile boolean isValidIndexedAttestation = false;

  private volatile Optional<IndexedAttestation> indexedAttestation = Optional.empty();
  private volatile Optional<Bytes32> committeeShufflingSeed = Optional.empty();
  private volatile OptionalInt receivedSubnetId;

  public static ValidateableAttestation from(final Spec spec, Attestation attestation) {
    return new ValidateableAttestation(
        spec, attestation, Optional.empty(), OptionalInt.empty(), false);
  }

  public static ValidateableAttestation fromValidator(final Spec spec, Attestation attestation) {
    return new ValidateableAttestation(
        spec, attestation, Optional.empty(), OptionalInt.empty(), true);
  }

  public static ValidateableAttestation fromNetwork(
      final Spec spec, Attestation attestation, int receivedSubnetId) {
    return new ValidateableAttestation(
        spec, attestation, Optional.empty(), OptionalInt.of(receivedSubnetId), false);
  }

  public static ValidateableAttestation aggregateFromValidator(
      final Spec spec, SignedAggregateAndProof attestation) {
    return new ValidateableAttestation(
        spec,
        attestation.getMessage().getAggregate(),
        Optional.of(attestation),
        OptionalInt.empty(),
        true);
  }

  public static ValidateableAttestation aggregateFromNetwork(
      final Spec spec, SignedAggregateAndProof attestation) {
    return new ValidateableAttestation(
        spec,
        attestation.getMessage().getAggregate(),
        Optional.of(attestation),
        OptionalInt.empty(),
        false);
  }

  private ValidateableAttestation(
      final Spec spec,
      Attestation attestation,
      Optional<SignedAggregateAndProof> aggregateAndProof,
      OptionalInt receivedSubnetId,
      boolean producedLocally) {
    this.spec = spec;
    this.maybeAggregate = aggregateAndProof;
    this.attestation = attestation;
    this.receivedSubnetId = receivedSubnetId;
    this.hashTreeRoot = Suppliers.memoize(attestation::hashTreeRoot);
    this.producedLocally = producedLocally;
  }

  public boolean isProducedLocally() {
    return producedLocally;
  }

  public boolean isValidIndexedAttestation() {
    return isValidIndexedAttestation;
  }

  public void setValidIndexedAttestation() {
    this.isValidIndexedAttestation = true;
  }

  public Optional<IndexedAttestation> getIndexedAttestation() {
    return indexedAttestation;
  }

  public Optional<Bytes32> getCommitteeShufflingSeed() {
    return committeeShufflingSeed;
  }

  public OptionalInt getReceivedSubnetId() {
    return receivedSubnetId;
  }

  public void setIndexedAttestation(IndexedAttestation indexedAttestation) {
    this.indexedAttestation = Optional.of(indexedAttestation);
  }

  public void saveCommitteeShufflingSeed(BeaconState state) {
    if (committeeShufflingSeed.isPresent()) {
      return;
    }

    Bytes32 committeeShufflingSeed =
        spec.getSeed(
            state,
            spec.computeEpochAtSlot(attestation.getData().getSlot()),
            Domain.BEACON_ATTESTER);
    this.committeeShufflingSeed = Optional.of(committeeShufflingSeed);
  }

  public boolean isGossiped() {
    return gossiped.get();
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

  public UInt64 getEarliestSlotForForkChoiceProcessing() {
    return attestation.getEarliestSlotForForkChoiceProcessing(spec);
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

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("spec", spec)
        .add("attestation", attestation)
        .add("maybeAggregate", maybeAggregate)
        .add("hashTreeRoot", hashTreeRoot)
        .add("gossiped", gossiped)
        .add("producedLocally", producedLocally)
        .add("isValidIndexedAttestation", isValidIndexedAttestation)
        .add("indexedAttestation", indexedAttestation)
        .add("committeeShufflingSeed", committeeShufflingSeed)
        .add("receivedSubnetId", receivedSubnetId)
        .toString();
  }
}
