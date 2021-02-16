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

package tech.pegasys.teku.protoarray;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.createProtoArrayForkChoiceStrategy;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.createStoreToManipulateVotes;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.getHash;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class VotesTest {

  @Test
  void votesTest() {
    VoteUpdater store = createStoreToManipulateVotes();

    ProtoArrayForkChoiceStrategy forkChoice =
        createProtoArrayForkChoiceStrategy(getHash(0), ZERO, ONE, ONE);

    List<UInt64> balances = new ArrayList<>(List.of(unsigned(1), unsigned(1)));

    // Ensure that the head starts at the finalized block.
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(0));

    // Add a block with a hash of 2.
    //
    //          0
    //         /
    //        2
    forkChoice.processBlock(ZERO, getHash(2), getHash(0), Bytes32.ZERO, ONE, ONE);

    // Ensure that the head is 2
    //
    //          0
    //         /
    // head-> 2
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // Add a block with a hash of 1 that comes off the genesis block (this is a fork compared
    // to the previous block).
    //
    //          0
    //         / \
    //        2   1
    forkChoice.processBlock(ZERO, getHash(1), getHash(0), Bytes32.ZERO, ONE, ONE);

    // Ensure that the head is still 2
    //
    //          0
    //         / \
    // head-> 2   1
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // Add a vote to block 1
    //
    //          0
    //         / \
    //        2   1 <- +vote
    forkChoice.processAttestation(store, unsigned(0), getHash(1), unsigned(2));

    // Ensure that the head is now 1, because 1 has a vote.
    //
    //          0
    //         / \
    //        2   1 <- head
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(1));

    // Add a vote to block 2
    //
    //           0
    //          / \
    // +vote-> 2   1
    forkChoice.processAttestation(store, unsigned(1), getHash(2), unsigned(2));

    // Ensure that the head is 2 since 1 and 2 both have a vote
    //
    //          0
    //         / \
    // head-> 2   1
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // Add block 3.
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    forkChoice.processBlock(ZERO, getHash(3), getHash(1), Bytes32.ZERO, ONE, ONE);

    // Ensure that the head is still 2
    //
    //          0
    //         / \
    // head-> 2   1
    //            |
    //            3
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // Move validator #0 vote from 1 to 3
    //
    //          0
    //         / \
    //        2   1 <- -vote
    //            |
    //            3 <- +vote
    forkChoice.processAttestation(store, unsigned(0), getHash(3), unsigned(3));

    // Ensure that the head is still 2
    //
    //          0
    //         / \
    // head-> 2   1
    //            |
    //            3
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // Move validator #1 vote from 2 to 1 (this is an equivocation, but fork choice doesn't
    // care)
    //
    //           0
    //          / \
    // -vote-> 2   1 <- +vote
    //             |
    //             3
    forkChoice.processAttestation(store, unsigned(1), getHash(1), unsigned(3));

    // Ensure that the head is now 3
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3 <- head
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(3));

    // Add block 4.
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    forkChoice.processBlock(ZERO, getHash(4), getHash(3), Bytes32.ZERO, ONE, ONE);

    // Ensure that the head is now 4
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4 <- head
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(4));

    // Add block 5, which has a justified epoch of 2.
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    //           /
    //          5 <- justified epoch = 2
    forkChoice.processBlock(ZERO, getHash(5), getHash(4), Bytes32.ZERO, unsigned(2), unsigned(2));

    // Ensure that 5 is filtered out and the head stays at 4.
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4 <- head
    //           /
    //          5
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(4));

    // Add block 6, which has a justified epoch of 0.
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    //           / \
    //          5   6 <- justified epoch = 0
    forkChoice.processBlock(ZERO, getHash(6), getHash(4), Bytes32.ZERO, unsigned(1), unsigned(1));

    // Move both votes to 5.
    //
    //           0
    //          / \
    //         2   1
    //             |
    //             3
    //             |
    //             4
    //            / \
    // +2 vote-> 5   6
    forkChoice.processAttestation(store, unsigned(0), getHash(5), unsigned(4));
    forkChoice.processAttestation(store, unsigned(1), getHash(5), unsigned(4));

    // Add blocks 7, 8 and 9. Adding these blocks helps test the `best_descendant`
    // functionality.
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    //           / \
    //          5   6
    //          |
    //          7
    //          |
    //          8
    //         /
    //         9
    forkChoice.processBlock(ZERO, getHash(7), getHash(5), Bytes32.ZERO, unsigned(2), unsigned(2));
    forkChoice.processBlock(ZERO, getHash(8), getHash(7), Bytes32.ZERO, unsigned(2), unsigned(2));
    forkChoice.processBlock(ZERO, getHash(9), getHash(8), Bytes32.ZERO, unsigned(2), unsigned(2));

    // Ensure that 6 is the head, even though 5 has all the votes. This is testing to ensure
    // that 5 is filtered out due to a differing justified epoch.
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    //           / \
    //          5   6 <- head
    //          |
    //          7
    //          |
    //          8
    //         /
    //         9
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(6));

    // Change fork-choice justified epoch to 1, and the start block to 5 and ensure that 9 is
    // the head.
    //
    // << Change justified epoch to 1 >>
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    //           / \
    //          5   6
    //          |
    //          7
    //          |
    //          8
    //         /
    // head-> 9
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(2), balances))
        .isEqualTo(getHash(9));

    // Move both votes to block 9
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    //           / \
    //          5   6
    //          |
    //          7
    //          |
    //          8
    //         /
    //        9 <- +2 votes
    forkChoice.processAttestation(store, unsigned(0), getHash(9), unsigned(5));
    forkChoice.processAttestation(store, unsigned(1), getHash(9), unsigned(5));

    // Add block 10
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    //           / \
    //          5   6
    //          |
    //          7
    //          |
    //          8
    //         / \
    //        9  10
    forkChoice.processBlock(ZERO, getHash(10), getHash(8), Bytes32.ZERO, unsigned(2), unsigned(2));

    // Double-check the head is still 9
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(2), balances))
        .isEqualTo(getHash(9));

    // Introduce 2 more validators into the system
    balances.addAll(List.of(unsigned(1), unsigned(1)));

    // Have the two new validators vote for 10
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    //           / \
    //          5   6
    //          |
    //          7
    //          |
    //          8
    //         / \
    //        9  10 <- +2 votes
    forkChoice.processAttestation(store, unsigned(2), getHash(10), unsigned(5));
    forkChoice.processAttestation(store, unsigned(3), getHash(10), unsigned(5));

    // Check the head is now 10. (due to lexicographical ordering
    // (when blocks have the same amount of votes))
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    //           / \
    //          5   6
    //          |
    //          7
    //          |
    //          8
    //         / \
    //        9  10 <- head
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(2), balances))
        .isEqualTo(getHash(10));

    // Set the balances of the last two validators to zero
    balances = new ArrayList<>(List.of(unsigned(1), unsigned(1), unsigned(0), unsigned(0)));

    // Check the head is 9 again.
    //
    //          .
    //          .
    //          .
    //          |
    //          8
    //         / \
    // head-> 9  10
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(2), balances))
        .isEqualTo(getHash(9));

    // Set the balances of the last two validators back to 1
    balances = new ArrayList<>(List.of(ONE, ONE, ONE, ONE));

    // Check the head is 10.
    //
    //          .
    //          .
    //          .
    //          |
    //          8
    //         / \
    //        9  10 <- head
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(2), balances))
        .isEqualTo(getHash(10));

    // Remove the last two validators
    balances = new ArrayList<>(List.of(ONE, ONE));

    // Check the head is 9 again.
    //
    //  (prior blocks omitted for brevity)
    //          .
    //          .
    //          .
    //          |
    //          8
    //         / \
    // head-> 9  10
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(2), balances))
        .isEqualTo(getHash(9));

    // Ensure that pruning below the prune threshold does not prune.
    forkChoice.setPruneThreshold(Integer.MAX_VALUE);
    forkChoice.applyUpdate(emptyList(), emptySet(), new Checkpoint(ONE, getHash(5)));
    assertThat(forkChoice.getTotalTrackedNodeCount()).isEqualTo(11);

    // Run find-head, ensure the no-op prune didn't change the head.
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(2), balances))
        .isEqualTo(getHash(9));

    // Ensure that pruning above the prune threshold does prune.
    //
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    // -------pruned here ------
    //          5   6
    //          |
    //          7
    //          |
    //          8
    //         / \
    //        9  10
    forkChoice.setPruneThreshold(1);
    forkChoice.applyUpdate(emptyList(), emptySet(), new Checkpoint(ONE, getHash(5)));
    assertThat(forkChoice.getTotalTrackedNodeCount()).isEqualTo(6);

    // Run find-head, ensure the prune didn't change the head.
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(2), balances))
        .isEqualTo(getHash(9));

    // Add block 11
    //
    //          5   6
    //          |
    //          7
    //          |
    //          8
    //         / \
    //        9  10
    //        |
    //        11
    forkChoice.processBlock(ZERO, getHash(11), getHash(9), Bytes32.ZERO, unsigned(2), unsigned(2));

    // Ensure the head is now 11
    //
    //          5   6
    //          |
    //          7
    //          |
    //          8
    //         / \
    //        9  10
    //        |
    // head-> 11
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(2), balances))
        .isEqualTo(getHash(11));
  }

  private UInt64 unsigned(final int i) {
    return UInt64.valueOf(i);
  }
}
