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

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static com.google.common.primitives.UnsignedLong.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.createProtoArrayForkChoiceStrategy;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.getHash;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class VotesTest {

  @Test
  void votesTest() {
    ProtoArrayForkChoiceStrategy forkChoice =
        createProtoArrayForkChoiceStrategy(getHash(0), ZERO, ONE, ONE);

    List<UnsignedLong> balances = new ArrayList<>(List.of(valueOf(1), valueOf(1)));

    // Ensure that the head starts at the finalized block.
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(0));

    // Add a block with a hash of 2.
    //
    //          0
    //         /
    //        2
    ProtoArrayForkChoiceStrategyUpdater updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(2), getHash(0), Bytes32.ZERO, ONE, ONE);
    updater.commit();

    // Ensure that the head is 2
    //
    //          0
    //         /
    // head-> 2
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // Add a block with a hash of 1 that comes off the genesis block (this is a fork compared
    // to the previous block).
    //
    //          0
    //         / \
    //        2   1
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(1), getHash(0), Bytes32.ZERO, ONE, ONE);
    updater.commit();

    // Ensure that the head is still 2
    //
    //          0
    //         / \
    // head-> 2   1
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // Add a vote to block 1
    //
    //          0
    //         / \
    //        2   1 <- +vote
    updater = forkChoice.updater();
    updater.processAttestation(valueOf(0), getHash(1), valueOf(2));
    updater.commit();

    // Ensure that the head is now 1, because 1 has a vote.
    //
    //          0
    //         / \
    //        2   1 <- head
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(1));

    // Add a vote to block 2
    //
    //           0
    //          / \
    // +vote-> 2   1
    updater = forkChoice.updater();
    updater.processAttestation(valueOf(1), getHash(2), valueOf(2));
    updater.commit();

    // Ensure that the head is 2 since 1 and 2 both have a vote
    //
    //          0
    //         / \
    // head-> 2   1
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // Add block 3.
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(3), getHash(1), Bytes32.ZERO, ONE, ONE);
    updater.commit();

    // Ensure that the head is still 2
    //
    //          0
    //         / \
    // head-> 2   1
    //            |
    //            3
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // Move validator #0 vote from 1 to 3
    //
    //          0
    //         / \
    //        2   1 <- -vote
    //            |
    //            3 <- +vote
    updater = forkChoice.updater();
    updater.processAttestation(valueOf(0), getHash(3), valueOf(3));
    updater.commit();

    // Ensure that the head is still 2
    //
    //          0
    //         / \
    // head-> 2   1
    //            |
    //            3
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // Move validator #1 vote from 2 to 1 (this is an equivocation, but fork choice doesn't
    // care)
    //
    //           0
    //          / \
    // -vote-> 2   1 <- +vote
    //             |
    //             3
    updater = forkChoice.updater();
    updater.processAttestation(valueOf(1), getHash(1), valueOf(3));
    updater.commit();

    // Ensure that the head is now 3
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3 <- head
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(3));

    // Add block 4.
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(4), getHash(3), Bytes32.ZERO, ONE, ONE);
    updater.commit();

    // Ensure that the head is now 4
    //
    //          0
    //         / \
    //        2   1
    //            |
    //            3
    //            |
    //            4 <- head
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(4));

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
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(5), getHash(4), Bytes32.ZERO, valueOf(2), valueOf(2));
    updater.commit();

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
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(4));

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
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(6), getHash(4), Bytes32.ZERO, valueOf(1), valueOf(1));
    updater.commit();

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
    updater = forkChoice.updater();
    updater.processAttestation(valueOf(0), getHash(5), valueOf(4));
    updater.processAttestation(valueOf(1), getHash(5), valueOf(4));
    updater.commit();

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
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(7), getHash(5), Bytes32.ZERO, valueOf(2), valueOf(2));
    updater.processBlock(ZERO, getHash(8), getHash(7), Bytes32.ZERO, valueOf(2), valueOf(2));
    updater.processBlock(ZERO, getHash(9), getHash(8), Bytes32.ZERO, valueOf(2), valueOf(2));
    updater.commit();

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
    assertThat(processHead(forkChoice, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(6));

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
    assertThat(processHead(forkChoice, valueOf(2), getHash(5), valueOf(2), balances))
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
    updater = forkChoice.updater();
    updater.processAttestation(valueOf(0), getHash(9), valueOf(5));
    updater.processAttestation(valueOf(1), getHash(9), valueOf(5));
    updater.commit();

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
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(10), getHash(8), Bytes32.ZERO, valueOf(2), valueOf(2));
    updater.commit();

    // Double-check the head is still 9
    assertThat(processHead(forkChoice, valueOf(2), getHash(5), valueOf(2), balances))
        .isEqualTo(getHash(9));

    // Introduce 2 more validators into the system
    balances.addAll(List.of(valueOf(1), valueOf(1)));

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
    updater = forkChoice.updater();
    updater.processAttestation(valueOf(2), getHash(10), valueOf(5));
    updater.processAttestation(valueOf(3), getHash(10), valueOf(5));
    updater.commit();

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
    processHead(forkChoice, valueOf(2), getHash(5), valueOf(2), balances);
    assertThat(forkChoice.getHead()).isEqualTo(getHash(10));

    // Set the balances of the last two validators to zero
    balances = new ArrayList<>(List.of(valueOf(1), valueOf(1), valueOf(0), valueOf(0)));

    // Check the head is 9 again.
    //
    //          .
    //          .
    //          .
    //          |
    //          8
    //         / \
    // head-> 9  10
    assertThat(processHead(forkChoice, valueOf(2), getHash(5), valueOf(2), balances))
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
    assertThat(processHead(forkChoice, valueOf(2), getHash(5), valueOf(2), balances))
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
    assertThat(processHead(forkChoice, valueOf(2), getHash(5), valueOf(2), balances))
        .isEqualTo(getHash(9));

    // Ensure that pruning below the prune threshold does not prune.
    forkChoice.setPruneThreshold(Integer.MAX_VALUE);
    updater = forkChoice.updater();
    updater.updateFinalizedBlock(getHash(5));
    updater.commit();
    assertThat(forkChoice.size()).isEqualTo(11);

    // Run find-head, ensure the no-op prune didn't change the head.
    assertThat(processHead(forkChoice, valueOf(2), getHash(5), valueOf(2), balances))
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
    updater = forkChoice.updater();
    updater.updateFinalizedBlock(getHash(5));
    updater.commit();
    assertThat(forkChoice.size()).isEqualTo(6);

    // Run find-head, ensure the prune didn't change the head.
    assertThat(processHead(forkChoice, valueOf(2), getHash(5), valueOf(2), balances))
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
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(11), getHash(9), Bytes32.ZERO, valueOf(2), valueOf(2));
    updater.commit();

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
    assertThat(processHead(forkChoice, valueOf(2), getHash(5), valueOf(2), balances))
        .isEqualTo(getHash(11));
  }

  private Bytes32 processHead(
      final ProtoArrayForkChoiceStrategy forkChoice,
      final UnsignedLong justifiedEpoch,
      final Bytes32 justifiedRoot,
      final UnsignedLong finalizedEpoch,
      final List<UnsignedLong> justifiedStateBalances) {
    final BeaconState justifiedCheckpointState = mock(BeaconState.class);
    when(justifiedCheckpointState.getBalances())
        .thenReturn(
            SSZList.createMutable(
                justifiedStateBalances, justifiedStateBalances.size(), UnsignedLong.class));

    ProtoArrayForkChoiceStrategyUpdater updater = forkChoice.updater();
    updater.updateHead(
        new Checkpoint(finalizedEpoch, getHash(0)),
        new Checkpoint(justifiedEpoch, justifiedRoot),
        justifiedCheckpointState);
    updater.commit();

    return forkChoice.getHead();
  }
}
