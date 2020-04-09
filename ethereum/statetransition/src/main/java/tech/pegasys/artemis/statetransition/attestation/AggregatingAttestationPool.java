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

package tech.pegasys.artemis.statetransition.attestation;

import static com.google.common.primitives.UnsignedLong.ONE;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.primitives.UnsignedLong;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.ssz.SSZTypes.SSZList;
import tech.pegasys.artemis.ssz.SSZTypes.SSZMutableList;

/**
 * Maintains a pool of attestations. Attestations can be retrieved either for inclusion in a block
 * or as an aggregate to publish as part of the naive attestation aggregation algorithm. In both
 * cases the returned attestations are aggregated to maximise the number of validators that can be
 * included.
 */
public class AggregatingAttestationPool {

  private final Map<Bytes, MatchingDataAttestationGroup> attestationGroupByDataHash =
      new LinkedHashMap<>();

  public synchronized void add(final Attestation attestation) {
    attestationGroupByDataHash
        .computeIfAbsent(
            attestation.getData().hash_tree_root(),
            key -> new MatchingDataAttestationGroup(attestation.getData()))
        .add(attestation);
  }

  public synchronized void remove(final Attestation attestation) {
    final Bytes32 dataRoot = attestation.getData().hash_tree_root();
    final MatchingDataAttestationGroup attestations = attestationGroupByDataHash.get(dataRoot);
    if (attestations == null) {
      return;
    }
    attestations.remove(attestation);
    if (attestations.isEmpty()) {
      attestationGroupByDataHash.remove(dataRoot);
    }
  }

  public synchronized SSZList<Attestation> getAttestationsForBlock(final UnsignedLong slot) {
    final SSZMutableList<Attestation> attestations = BeaconBlockBodyLists.createAttestations();
    attestationGroupByDataHash.values().stream()
        .filter(group -> canBeIncluded(group, slot))
        .flatMap(MatchingDataAttestationGroup::stream)
        .limit(attestations.getMaxSize())
        .forEach(attestations::add);
    return attestations;
  }

  public synchronized Optional<Attestation> createAggregateFor(
      final AttestationData attestationData) {
    return Optional.ofNullable(attestationGroupByDataHash.get(attestationData.hash_tree_root()))
        .flatMap(attestations -> attestations.stream().findFirst());
  }

  private boolean canBeIncluded(final MatchingDataAttestationGroup group, final UnsignedLong slot) {
    final AttestationData attestationData = group.getAttestationData();
    return attestationData.getEarliestSlotForProcessing().compareTo(slot) <= 0
        && isPreviousEpochOrLater(attestationData, slot);
  }

  private boolean isPreviousEpochOrLater(
      final AttestationData attestationData, final UnsignedLong slot) {
    return compute_epoch_at_slot(attestationData.getSlot())
            .plus(ONE)
            .compareTo(compute_epoch_at_slot(slot))
        >= 0;
  }
}
