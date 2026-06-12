/*
 * Copyright Consensys Software Inc., 2026
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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.newSetFromMap;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.storage.protoarray.DeferredVotes;

public class VoteUpdates implements DeferredVotes {
  private final UInt64 slot;
  private final Map<BlockRootAndFullPayloadHint, Collection<UInt64>>
      votingIndicesByBlockRootAndFullPayloadHint = new ConcurrentHashMap<>();

  public VoteUpdates(final UInt64 slot) {
    this.slot = slot;
  }

  @Override
  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public void forEachDeferredVote(final DeferredVoteConsumer consumer) {
    votingIndicesByBlockRootAndFullPayloadHint.forEach(
        (key, indices) ->
            indices.forEach(
                validatorIndex ->
                    consumer.accept(key.blockRoot(), validatorIndex, key.fullPayloadHint())));
  }

  public boolean isEmpty() {
    return votingIndicesByBlockRootAndFullPayloadHint.values().stream()
        .allMatch(Collection::isEmpty);
  }

  public void addAttestation(
      final IndexedAttestationLight attestation, final boolean fullPayloadHint) {
    checkArgument(
        attestation.data().getSlot().equals(slot),
        "Attempting to store vote update for wrong slot. Expected %s but got %s",
        slot,
        attestation.data().getSlot());
    final Bytes32 blockRoot = attestation.data().getBeaconBlockRoot();
    votingIndicesByBlockRootAndFullPayloadHint
        .computeIfAbsent(
            new BlockRootAndFullPayloadHint(blockRoot, fullPayloadHint),
            __ -> newSetFromMap(new ConcurrentHashMap<>()))
        .addAll(attestation.attestingIndices());
  }

  public void addVote(
      final Bytes32 blockRoot, final UInt64 validatorIndex, final boolean fullPayloadHint) {
    votingIndicesByBlockRootAndFullPayloadHint
        .computeIfAbsent(
            new BlockRootAndFullPayloadHint(blockRoot, fullPayloadHint),
            __ -> newSetFromMap(new ConcurrentHashMap<>()))
        .add(validatorIndex);
  }

  private record BlockRootAndFullPayloadHint(Bytes32 blockRoot, boolean fullPayloadHint) {}
}
