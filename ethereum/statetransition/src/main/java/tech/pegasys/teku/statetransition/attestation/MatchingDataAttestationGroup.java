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

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * Defines the contract for a group of attestations sharing the same AttestationData. Handles
 * aggregation and tracking of included validators.
 */
public interface MatchingDataAttestationGroup extends Iterable<ValidatableAttestation> {

  /**
   * Gets the common AttestationData shared by all attestations in this group.
   *
   * @return The shared AttestationData.
   */
  AttestationData getAttestationData();

  /**
   * Adds an attestation to this group if it contains new, unseen validator signatures.
   *
   * @param attestation The attestation to add.
   * @return True if the attestation was added (contributed new information), false otherwise.
   */
  boolean add(ValidatableAttestation attestation);

  /**
   * Returns an iterator that produces aggregated attestations covering all validators in this
   * group, optionally filtered by a specific committee index.
   *
   * @param committeeIndex Optional committee index to filter/focus aggregation (Electra).
   * @return An iterator producing aggregated ValidatableAttestations.
   */
  Iterator<ValidatableAttestation> iterator(Optional<UInt64> committeeIndex);

  /**
   * Returns a stream that produces aggregated attestations covering all validators in this group.
   * Equivalent to stream(Optional.empty()).
   *
   * @return A stream producing aggregated ValidatableAttestations.
   */
  Stream<ValidatableAttestation> stream();

  /**
   * Returns a stream that produces aggregated attestations covering all validators in this group,
   * optionally filtered by a specific committee index.
   *
   * @param committeeIndex Optional committee index to filter/focus aggregation (Electra).
   * @return A stream producing aggregated ValidatableAttestations.
   */
  Stream<ValidatableAttestation> stream(Optional<UInt64> committeeIndex);

  /**
   * Returns a stream that produces aggregated attestations, potentially filtered by committee index
   * and whether committee bits are required.
   *
   * @param committeeIndex Optional committee index filter.
   * @param requiresCommitteeBits Whether the context requires attestations with committee bits.
   * @return A stream producing aggregated ValidatableAttestations.
   */
  Stream<ValidatableAttestation> stream(
      Optional<UInt64> committeeIndex, boolean requiresCommitteeBits);

  /**
   * Returns a spliterator for producing aggregated attestations, optionally filtered by a specific
   * committee index.
   *
   * @param committeeIndex Optional committee index filter.
   * @return A spliterator producing aggregated ValidatableAttestations.
   */
  Spliterator<ValidatableAttestation> spliterator(Optional<UInt64> committeeIndex);

  /**
   * Checks if this group contains any attestations.
   *
   * @return True if the group is empty, false otherwise.
   */
  boolean isEmpty();

  /**
   * Returns the approximate number of individual (non-aggregated) attestations currently held
   * within this group. Note: In concurrent implementations, this might be an estimate.
   *
   * @return The number of attestations in the group.
   */
  int size();

  /**
   * Processes an attestation included in a block. Marks the validators in the attestation as seen
   * and removes any attestations from the group that are now fully covered.
   *
   * @param slot The slot of the block containing the attestation.
   * @param attestation The attestation included in the block.
   * @return The number of attestations (or validator contributions) effectively removed from the
   *     pool because they are now fully covered.
   */
  int onAttestationIncludedInBlock(UInt64 slot, Attestation attestation);

  /**
   * Handles a re-organization event by recalculating the set of validators considered included on
   * the canonical chain up to the common ancestor slot.
   *
   * @param commonAncestorSlot The slot of the common ancestor block.
   */
  void onReorg(UInt64 commonAncestorSlot);

  /**
   * Checks if the committee shuffling seed associated with this group (if determined) is present in
   * the provided set of valid seeds.
   *
   * @param validSeeds A set of valid committee shuffling seeds.
   * @return True if the group's seed matches one in the set, false otherwise or if the seed is
   *     unknown.
   */
  boolean matchesCommitteeShufflingSeed(Set<Bytes32> validSeeds);

  /**
   * Validates the common AttestationData of this group against the given state.
   *
   * @param stateAtBlockSlot The beacon state to validate against.
   * @param spec The specification instance.
   * @return True if the AttestationData is valid for the state, false otherwise.
   */
  boolean isValid(BeaconState stateAtBlockSlot, Spec spec);
}
