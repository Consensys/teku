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

import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.storage.protoarray.DeferredVotes;

public class VoteUpdates implements DeferredVotes {
  private final UInt64 slot;

  // Keyed by validator index so that each validator holds a single vote for the slot. A validator's
  // first vote wins (putIfAbsent), mirroring the spec's update_latest_messages, which only
  // overwrites a latest message when the new attestation's target epoch is strictly greater (a
  // same-epoch vote never overwrites). This is why a validator that attests to two competing blocks
  // in one slot (equivocation) is applied to its first-received vote rather than to whichever block
  // root a root-keyed map happened to iterate first. A navigable map keeps iteration deterministic
  // (ordered by validator index).
  private final NavigableMap<UInt64, BlockRootAndFullPayloadHint> voteByValidatorIndex =
      new ConcurrentSkipListMap<>();

  public VoteUpdates(final UInt64 slot) {
    this.slot = slot;
  }

  @Override
  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public void forEachDeferredVote(final DeferredVoteConsumer consumer) {
    voteByValidatorIndex.forEach(
        (validatorIndex, vote) ->
            consumer.accept(vote.blockRoot(), validatorIndex, vote.fullPayloadHint()));
  }

  public boolean isEmpty() {
    return voteByValidatorIndex.isEmpty();
  }

  public void addAttestation(
      final IndexedAttestationLight attestation, final boolean fullPayloadHint) {
    checkArgument(
        attestation.data().getSlot().equals(slot),
        "Attempting to store vote update for wrong slot. Expected %s but got %s",
        slot,
        attestation.data().getSlot());
    final BlockRootAndFullPayloadHint vote =
        new BlockRootAndFullPayloadHint(attestation.data().getBeaconBlockRoot(), fullPayloadHint);
    attestation.attestingIndices().forEach(index -> voteByValidatorIndex.putIfAbsent(index, vote));
  }

  public void addVote(
      final Bytes32 blockRoot, final UInt64 validatorIndex, final boolean fullPayloadHint) {
    voteByValidatorIndex.putIfAbsent(
        validatorIndex, new BlockRootAndFullPayloadHint(blockRoot, fullPayloadHint));
  }

  private record BlockRootAndFullPayloadHint(Bytes32 blockRoot, boolean fullPayloadHint) {}
}
