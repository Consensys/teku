/*
 * Copyright 2021 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.createProtoArrayForkChoiceStrategy;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.createStoreToManipulateVotes;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.getHash;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;

public class ProtoArrayTest {
  VoteUpdater store = createStoreToManipulateVotes();
  private static final UInt64 TWO = UInt64.valueOf(2);

  private static final UInt64 FIRST_EPOCH = UInt64.valueOf(1100);
  private static final UInt64 SECOND_EPOCH = FIRST_EPOCH.increment();

  private final List<UInt64> balances = new ArrayList<>(List.of(ONE, ONE));
  private ProtoArrayForkChoiceStrategy forkChoice;

  @BeforeEach
  void setup() {
    forkChoice = createProtoArrayForkChoiceStrategy(getHash(0), ZERO, FIRST_EPOCH, FIRST_EPOCH);
  }
  /**
   * Builds a chain of a single node (0)
   *
   * <p>0 <- head
   */
  @Test
  void shouldCreateProtoArrayAndAddNode() {
    assertThat(forkChoice.findHead(store, FIRST_EPOCH, getHash(0), FIRST_EPOCH, balances))
        .isEqualTo(getHash(0));

    assertThat(forkChoice.getTotalTrackedNodeCount()).isEqualTo(1);
    assertThat(forkChoice.getChainHeads()).containsOnlyKeys(getHash(0));
  }

  /**
   * Builds a chain of two nodes
   *
   * <p>0 / 2 <- head
   */
  @Test
  void shouldAddSecondNodeAndUpdateHead() {
    forkChoice.processBlock(ZERO, getHash(2), getHash(0), Bytes32.ZERO, ONE, ONE);
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    assertThat(forkChoice.getTotalTrackedNodeCount()).isEqualTo(2);
    assertThat(forkChoice.getChainHeads()).containsOnlyKeys(getHash(2));
  }

  /**
   * Fork from a common ancestor
   *
   * <p>0 / \ head-> 2 1
   */
  @Test
  void shouldForkAndDetermineHeadViaVotes() {
    forkChoice.processBlock(ZERO, getHash(2), getHash(0), Bytes32.ZERO, ONE, ONE);
    forkChoice.processBlock(ZERO, getHash(1), getHash(0), Bytes32.ZERO, ONE, ONE);

    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));
    assertThat(forkChoice.getTotalTrackedNodeCount()).isEqualTo(3);
    assertThat(forkChoice.getChainHeads()).containsOnlyKeys(getHash(2), getHash(1));
  }

  /**
   * Fork from a common ancestor, and vote
   *
   * <p>0 / \ 2 1 <- head
   */
  @Test
  void shouldForkAndChangeHeadFromVotes() {
    forkChoice.processBlock(ZERO, getHash(2), getHash(0), Bytes32.ZERO, ONE, ONE);
    forkChoice.processBlock(ZERO, getHash(1), getHash(0), Bytes32.ZERO, ONE, ONE);
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    forkChoice.processAttestation(store, ZERO, getHash(1), TWO);
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(1));
  }

  /**
   * Fork from a common ancestor, and vote
   *
   * <p>0 / \ head-> 2 1
   */
  @Test
  void shouldForkAndChangeHead() {
    forkChoice.processBlock(ZERO, getHash(2), getHash(0), Bytes32.ZERO, ONE, ONE);
    forkChoice.processBlock(ZERO, getHash(1), getHash(0), Bytes32.ZERO, ONE, ONE);
    forkChoice.processAttestation(store, ZERO, getHash(1), TWO);
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(1));

    forkChoice.processAttestation(store, ONE, getHash(2), TWO);
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));
  }

  /** Add node to the non head fork, ensure head stays the same 0 / \ head-> 2 1 \ 3 */
  @Test
  void shouldAddBlocksToForks() {
    forkChoice.processBlock(ZERO, getHash(2), getHash(0), Bytes32.ZERO, ONE, ONE);
    forkChoice.processBlock(ZERO, getHash(1), getHash(0), Bytes32.ZERO, ONE, ONE);
    forkChoice.processAttestation(store, ZERO, getHash(2), TWO);
    forkChoice.processBlock(ZERO, getHash(3), getHash(1), Bytes32.ZERO, ONE, ONE);

    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));
    assertThat(forkChoice.getChainHeads()).containsOnlyKeys(getHash(2), getHash(3));
  }

  /**
   * Add a node to the non head fork, votes come in for the non head fork, head changes 0 / \
   * initial head -> 2 1 \ 3 <- head after votes
   */
  @Test
  void shouldSwitchForksBasedOnVotes() {
    forkChoice.processBlock(ZERO, getHash(2), getHash(0), Bytes32.ZERO, ONE, ONE);
    forkChoice.processBlock(ZERO, getHash(1), getHash(0), Bytes32.ZERO, ONE, ONE);
    // validator 1 votes for block 2
    // validator 0 votes for block 1
    // tally: block2: 1 ; block1: 1
    forkChoice.processAttestation(store, ONE, getHash(2), TWO);
    forkChoice.processAttestation(store, ZERO, getHash(1), TWO);

    // block 3 added, doesnt change head, as head was on a different fork
    forkChoice.processBlock(ZERO, getHash(3), getHash(1), Bytes32.ZERO, ONE, ONE);
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // validator 0 now votes for block 3
    // tally: block3: 1 ; block2: 1
    forkChoice.processAttestation(store, ZERO, getHash(3), UInt64.valueOf(3));
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(2));

    // validator 1 votes for block 1, which is technically equivocation but fork choice doesnt care
    // tally: block3: 1 ; block 1: 1
    forkChoice.processAttestation(store, ONE, getHash(1), UInt64.valueOf(3));
    // all votes are on block 1 and 3, so 3 should now be head.
    assertThat(forkChoice.findHead(store, ONE, getHash(0), ONE, balances)).isEqualTo(getHash(3));
  }

  /**
   * When a block is added to the head of the chain, the head moves forward to the new block 0 / \ 2
   * 1(+) <- initial head \ 3 <- head when added
   */
  @Test
  void shouldUpdateHeadIfHeadChainGrows() {
    processBlock(2, 0, ONE);
    processBlock(1, 0, ONE);

    // validator 0 votes for block 1
    processAttestation(ZERO, 1, TWO);
    assertThat(findHeadAtEpoch(ONE)).isEqualTo(getHash(1));

    processBlock(3, 1, ONE);
    assertThat(findHeadAtEpoch(ONE)).isEqualTo(getHash(3));
  }

  /**
   * findHead at epoch filters on the justified epoch 0 | head -> 1 <- justified epoch 1 | 2 <-
   * justified epoch 2
   */
  @Test
  void shouldFindHeadFilteringOnEpoch() {
    processBlock(1, 0, ONE);
    processBlock(2, 1, TWO);
    assertThat(findHeadAtEpoch(ONE)).isEqualTo(getHash(1));
  }

  /**
   * findHead at epoch filters on the justified epoch 0 | 1 <- justified epoch 1 | head -> 2 <-
   * justified epoch 2
   */
  @Test
  void shouldFindHeadFilteringOnEpoch2() {
    processBlock(1, 0, ONE);
    processBlock(2, 1, TWO);
    assertThat(findHeadAtEpoch(TWO)).isEqualTo(getHash(2));
  }

  /**
   * block 1 is justified at epoch 1, block 2 is justified at epoch 2, find head at epoch 1 - block
   * 2 has votes, but it's justified at epoch 2, and should be ignored 0 / \ (++) 2 1 <- head
   */
  @Test
  void shouldFilterOutVoteBlockIfJustifiedEpochGreater() {
    processBlock(1, 0, ONE);
    processBlock(2, 0, TWO);
    processAttestation(ZERO, 1, TWO);
    processAttestation(ONE, 1, TWO);

    assertThat(findHeadAtEpoch(ONE)).isEqualTo(getHash(1));
  }

  private void processBlock(
      final int blockNumber, final int parentBlockNumber, final UInt64 epoch) {
    forkChoice.processBlock(
        ZERO, getHash(blockNumber), getHash(parentBlockNumber), Bytes32.ZERO, epoch, epoch);
  }

  private void processAttestation(
      final UInt64 validatorIndex, final int blockNumber, final UInt64 targetEpoch) {
    forkChoice.processAttestation(store, validatorIndex, getHash(blockNumber), targetEpoch);
  }

  private Bytes32 findHeadAtEpoch(final UInt64 epoch) {
    return findHeadAtEpoch(epoch, 0);
  }

  private Bytes32 findHeadAtEpoch(final UInt64 epoch, final int justifiedBlockNumber) {
    return forkChoice.findHead(store, epoch, getHash(justifiedBlockNumber), epoch, balances);
  }
}
