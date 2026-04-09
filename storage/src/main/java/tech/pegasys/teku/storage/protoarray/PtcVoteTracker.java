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

package tech.pegasys.teku.storage.protoarray;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Tracks the per-root PTC vote material consumed by the Gloas fork-choice helpers
 * `is_payload_timely(...)` and `is_payload_data_available(...)`.
 *
 * <p>This is a storage-side representation of the state updated by `notify_ptc_messages(...)` and
 * later queried from `should_extend_payload(...)`:
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-notify_ptc_messages
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-is_payload_timely
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-is_payload_data_available
 *
 * <p>Teku stores the information as per-root validator sets because fork choice only needs the
 * resulting counts, not the spec's exact vector layout.
 */
class PtcVoteTracker {

  private record VotesPerValidator(Set<UInt64> payload, Set<UInt64> data) {}

  private final Map<Bytes32, VotesPerValidator> votesByRoot = new ConcurrentHashMap<>();

  void recordVote(
      final Bytes32 blockRoot,
      final UInt64 validatorIndex,
      final boolean payloadPresent,
      final boolean blobDataAvailable) {
    votesByRoot.compute(
        blockRoot,
        (__, existingVotes) -> {
          final VotesPerValidator updatedVotes =
              existingVotes != null
                  ? existingVotes
                  : new VotesPerValidator(
                      ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet());
          if (payloadPresent) {
            updatedVotes.payload.add(validatorIndex);
          } else {
            updatedVotes.payload.remove(validatorIndex);
          }

          if (blobDataAvailable) {
            updatedVotes.data.add(validatorIndex);
          } else {
            updatedVotes.data.remove(validatorIndex);
          }
          return updatedVotes;
        });
  }

  int getPayloadPresentVoteCount(final Bytes32 blockRoot) {
    final VotesPerValidator votes = votesByRoot.get(blockRoot);
    return votes != null ? votes.payload.size() : 0;
  }

  int getDataAvailableVoteCount(final Bytes32 blockRoot) {
    final VotesPerValidator votes = votesByRoot.get(blockRoot);
    return votes != null ? votes.data.size() : 0;
  }

  void remove(final Bytes32 blockRoot) {
    votesByRoot.remove(blockRoot);
  }

  void removeIf(final Predicate<Bytes32> shouldRemove) {
    votesByRoot.keySet().removeIf(shouldRemove);
  }
}
