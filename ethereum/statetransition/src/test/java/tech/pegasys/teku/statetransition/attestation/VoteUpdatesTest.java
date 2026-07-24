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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class VoteUpdatesTest {

  private static final Bytes32 BLOCK_A =
      Bytes32.fromHexString("0x1111111111111111111111111111111111111111111111111111111111111111");
  private static final Bytes32 BLOCK_B =
      Bytes32.fromHexString("0x2222222222222222222222222222222222222222222222222222222222222222");

  private final VoteUpdates voteUpdates = new VoteUpdates(UInt64.valueOf(16));

  @Test
  void keepsOnlyTheFirstVoteWhenAValidatorVotesTwoBlocksInTheSameSlot() {
    // A validator equivocating within a slot: the first vote must win, matching the spec's
    // update_latest_messages (a same-epoch vote never overwrites the latest message).
    voteUpdates.addVote(BLOCK_A, UInt64.ZERO, false);
    voteUpdates.addVote(BLOCK_B, UInt64.ZERO, false);

    assertThat(collectVotes()).containsExactly(new Vote(BLOCK_A, UInt64.ZERO, false));
  }

  @Test
  void keepsFirstVoteRegardlessOfWhichBlockIsSeenFirst() {
    voteUpdates.addVote(BLOCK_B, UInt64.ZERO, false);
    voteUpdates.addVote(BLOCK_A, UInt64.ZERO, false);

    assertThat(collectVotes()).containsExactly(new Vote(BLOCK_B, UInt64.ZERO, false));
  }

  @Test
  void recordsDistinctValidatorsVotingDifferentBlocks() {
    voteUpdates.addVote(BLOCK_A, UInt64.ZERO, false);
    voteUpdates.addVote(BLOCK_B, UInt64.ONE, false);

    assertThat(collectVotes())
        .containsExactlyInAnyOrder(
            new Vote(BLOCK_A, UInt64.ZERO, false), new Vote(BLOCK_B, UInt64.ONE, false));
  }

  private List<Vote> collectVotes() {
    final List<Vote> votes = new ArrayList<>();
    voteUpdates.forEachDeferredVote(
        (blockRoot, validatorIndex, fullPayloadHint) ->
            votes.add(new Vote(blockRoot, validatorIndex, fullPayloadHint)));
    return votes;
  }

  private record Vote(Bytes32 blockRoot, UInt64 validatorIndex, boolean fullPayloadHint) {}
}
