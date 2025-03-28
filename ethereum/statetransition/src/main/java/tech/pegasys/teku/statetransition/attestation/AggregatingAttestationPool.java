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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * Defines the contract for an aggregating attestation pool. Maintains a pool of attestations for
 * block production and aggregation.
 */
public interface AggregatingAttestationPool extends SlotEventsChannel {

  /** The valid attestation retention period is 64 slots in deneb */
  long ATTESTATION_RETENTION_SLOTS = 64;

  /**
   * Default maximum number of attestations to store in the pool.
   *
   * <p>With 2 million active validators, we'd expect around 62_500 attestations per slot; so 3
   * slots worth of attestations is almost 187_500.
   *
   * <p>Strictly to cache all attestations for a full 2 epochs is significantly larger than this
   * cache.
   */
  int DEFAULT_MAXIMUM_ATTESTATION_COUNT = 187_500;

  /**
   * Adds a validated attestation to the pool.
   *
   * @param attestation The attestation to add.
   */
  void add(ValidatableAttestation attestation);

  /**
   * Processes attestations that have been included in a block, potentially removing validators
   * covered by these attestations from the pool.
   *
   * @param slot The slot of the block containing the attestations.
   * @param attestations The attestations included in the block.
   */
  void onAttestationsIncludedInBlock(UInt64 slot, Iterable<Attestation> attestations);

  /**
   * Returns the current number of attestations (individual validator signatures, before
   * aggregation) stored in the pool.
   *
   * @return The total size of the pool.
   */
  int getSize();

  /**
   * Retrieves attestations suitable for inclusion in a block being proposed at the given state's
   * slot. Attestations are aggregated and filtered based on validity, fork choice rules, and epoch
   * limits.
   *
   * @param stateAtBlockSlot The state corresponding to the slot of the block being proposed.
   * @param forkChecker A checker to ensure attestations are from the correct fork.
   * @return A list of attestations ready for block inclusion.
   */
  SszList<Attestation> getAttestationsForBlock(
      BeaconState stateAtBlockSlot, AttestationForkChecker forkChecker);

  /**
   * Retrieves attestations from the pool, optionally filtered by slot and committee index. This is
   * typically used for P2P requests or diagnostics.
   *
   * @param maybeSlot Optional slot to filter attestations by.
   * @param maybeCommitteeIndex Optional committee index to filter attestations by.
   * @return A list of matching attestations.
   */
  List<Attestation> getAttestations(
      Optional<UInt64> maybeSlot, Optional<UInt64> maybeCommitteeIndex);

  /**
   * Creates an aggregate attestation based on the attestations currently in the pool that match the
   * given attestation data hash root. Optionally filters by committee index for Electra
   * aggregation.
   *
   * @param attestationHashTreeRoot The hash tree root of the AttestationData to aggregate for.
   * @param committeeIndex Optional committee index filter (for Electra).
   * @return An Optional containing the best aggregate found, or empty if no suitable attestations
   *     exist.
   */
  Optional<ValidatableAttestation> createAggregateFor(
      Bytes32 attestationHashTreeRoot, Optional<UInt64> committeeIndex);

  /**
   * Handles a re-organization event. Attestations included in blocks after the common ancestor slot
   * need to be reconsidered.
   *
   * @param commonAncestorSlot The slot of the latest block that is common to both the old and new
   *     chains.
   */
  void onReorg(UInt64 commonAncestorSlot);

  // onSlot(UInt64 slot) is inherited from SlotEventsChannel
}
