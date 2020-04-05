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

import com.google.common.primitives.UnsignedLong;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableList;

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
        .forEach(attestations::add);
    return attestations;
  }

  public synchronized Optional<Attestation> createAggregateFor(
      final AttestationData attestationData) {
    final MatchingDataAttestationGroup attestations =
        attestationGroupByDataHash.get(attestationData.hash_tree_root());
    if (attestations == null) {
      return Optional.empty();
    }

    return attestations.stream().findFirst();
  }

  public boolean canBeIncluded(final MatchingDataAttestationGroup group, final UnsignedLong slot) {
    // TODO: Hit all cases in Attestation.getEarliestSlotForProcessing
    return group.getAttestationData().getSlot().compareTo(slot) < 0;
  }

  public Stream<Attestation> stream() {
    return attestationGroupByDataHash.values().stream()
        .flatMap(MatchingDataAttestationGroup::stream);
  }
}
