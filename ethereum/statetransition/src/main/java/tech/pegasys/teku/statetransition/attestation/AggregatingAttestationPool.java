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

public interface AggregatingAttestationPool extends SlotEventsChannel {
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

  /** The valid attestation retention period is 64 slots in deneb */
  long ATTESTATION_RETENTION_SLOTS = 64;

  int getSize();

  void add(ValidatableAttestation attestation);

  SszList<Attestation> getAttestationsForBlock(
      BeaconState stateAtBlockSlot, AttestationForkChecker forkChecker);

  Optional<Attestation> createAggregateFor(
      Bytes32 attestationHashTreeRoot, Optional<UInt64> committeeIndex);

  List<Attestation> getAttestations(
      Optional<UInt64> maybeSlot, Optional<UInt64> maybeCommitteeIndex);

  void onAttestationsIncludedInBlock(UInt64 slot, Iterable<Attestation> attestations);

  void onReorg(UInt64 commonAncestorSlot);
}
