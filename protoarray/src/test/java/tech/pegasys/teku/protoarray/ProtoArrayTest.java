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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.protoarray.ProtoNodeValidationStatus.OPTIMISTIC;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.StubVoteUpdater;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ProtoArrayTest {
  private final Checkpoint GENESIS_CHECKPOINT = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalPhase0());
  private final VoteUpdater voteUpdater = new StubVoteUpdater();

  private final Bytes32 block1a = dataStructureUtil.randomBytes32();
  private final Bytes32 block1b = dataStructureUtil.randomBytes32();
  private final Bytes32 block2a = dataStructureUtil.randomBytes32();
  private final Bytes32 block2b = dataStructureUtil.randomBytes32();
  private final Bytes32 block3a = dataStructureUtil.randomBytes32();

  private ProtoArray protoArray =
      new ProtoArrayBuilder()
          .justifiedCheckpoint(GENESIS_CHECKPOINT)
          .finalizedCheckpoint(GENESIS_CHECKPOINT)
          .build();

  @BeforeEach
  void setUp() {
    addOptimisticBlock(0, Bytes32.ZERO, Bytes32.ZERO);
  }

  @Test
  void findHead_shouldAlwaysConsiderJustifiedNodeAsViableHead() {
    // The justified checkpoint is the default chain head to use when there are no other blocks
    // considered viable, so ensure we always accept it as head
    final Bytes32 justifiedRoot = dataStructureUtil.randomBytes32();
    protoArray =
        new ProtoArrayBuilder()
            .justifiedCheckpoint(new Checkpoint(UInt64.ONE, justifiedRoot))
            .finalizedCheckpoint(new Checkpoint(UInt64.ONE, justifiedRoot))
            .initialEpoch(UInt64.ZERO)
            .build();
    // Justified block will have justified and finalized epoch of 0 which doesn't match the current
    // so would normally be not viable, but we should allow it anyway.
    addValidBlock(12, justifiedRoot, dataStructureUtil.randomBytes32());
    final ProtoNode head = protoArray.findHead(justifiedRoot, UInt64.ONE, UInt64.ONE).orElseThrow();
    assertThat(head).isEqualTo(protoArray.getProtoNode(justifiedRoot).orElseThrow());
  }

  @Test
  void findHead_shouldExcludeOptimisticBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);

    assertStrictHead(block1a);
  }

  @Test
  void findHead_shouldNotHaveHeadWhenJustifiedBlockIsOptimistic() {
    // Finalized root is fully validated, but head must descend from the justified checkpoint
    protoArray.markNodeValid(GENESIS_CHECKPOINT.getRoot());
    final Bytes32 justifiedRoot = dataStructureUtil.randomBytes32();
    addOptimisticBlock(12, justifiedRoot, GENESIS_CHECKPOINT.getRoot());

    assertThat(protoArray.findHead(justifiedRoot, UInt64.ONE, UInt64.ZERO)).isEmpty();
  }

  @Test
  void findHead_shouldIncludeValidatedBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(2, block2a, block1a);

    assertStrictHead(block2a);
  }

  @Test
  void findHead_shouldExcludeBlocksWithInvalidPayload() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);

    assertStrictHead(block1a);

    protoArray.markNodeInvalid(block2a);

    assertStrictHead(block1a);
  }

  @Test
  void findHead_shouldExcludeBlocksDescendedFromAnInvalidBlock() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertStrictHead(block1a);

    protoArray.markNodeInvalid(block2a);

    assertStrictHead(block1a);
  }

  @Test
  void findHead_shouldThrowFatalServiceExceptionWhenAllBlocksAreInvalid() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    protoArray.markNodeInvalid(GENESIS_CHECKPOINT.getRoot());

    assertThatThrownBy(
            () -> protoArray.findHead(GENESIS_CHECKPOINT.getRoot(), UInt64.ZERO, UInt64.ZERO))
        .isInstanceOf(FatalServiceFailureException.class);
  }

  /**
   * Even the finalized block is only optimistically sync'd so we can't find a strict head from
   * within the protoarray. Something at a higher level will have to provide the highest fully
   * validated finalized block as the chain head instead.
   */
  @Test
  void findHead_shouldReturnEmptyWhenAllBlocksInChainAreOptimistic() {
    protoArray
        .getProtoNode(GENESIS_CHECKPOINT.getRoot())
        .orElseThrow()
        .setValidationStatus(OPTIMISTIC);
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertThat(protoArray.findHead(GENESIS_CHECKPOINT.getRoot(), UInt64.ZERO, UInt64.ZERO))
        .isEmpty();
  }

  @Test
  void findOptimisticHead_shouldIncludeOptimisticBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);

    assertOptimisticHead(block2a);
  }

  @Test
  void findOptimisticHead_shouldIncludeValidBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(2, block2a, block1a);

    assertOptimisticHead(block2a);
  }

  @Test
  void findOptimisticHead_shouldExcludeInvalidBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);

    assertOptimisticHead(block2a);

    protoArray.markNodeInvalid(block2a);

    assertOptimisticHead(block1a);
  }

  @Test
  void findOptimisticHead_shouldExcludeBlocksDescendedFromAnInvalidBlock() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    // Apply score changes to ensure that the best descendant index is updated
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3a));
    assertOptimisticHead(block3a);

    protoArray.markNodeInvalid(block2a);

    assertOptimisticHead(block1a);
  }

  @Test
  void findOptimisticHead_shouldAcceptOptimisticBlocksWithInvalidAncestors() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    // Apply score changes to ensure that the best descendant index is updated
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3a));
    assertOptimisticHead(block3a);

    protoArray.markNodeInvalid(block3a);

    assertOptimisticHead(block2a);
  }

  @Test
  void findOptimisticHead_shouldThrowExceptionWhenAllBlocksAreInvalid() {
    // ProtoArray always contains the finalized block, if it's invalid there's a big problem.
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertOptimisticHead(block3a);

    protoArray.markNodeInvalid(GENESIS_CHECKPOINT.getRoot());

    assertThatThrownBy(
            () ->
                protoArray.findOptimisticHead(
                    GENESIS_CHECKPOINT.getRoot(), UInt64.ZERO, UInt64.ZERO))
        .isInstanceOf(FatalServiceFailureException.class);
  }

  @Test
  void shouldConsiderWeightFromVotesForValidBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1b, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(2, block2a, block1a);
    addValidBlock(2, block2b, block1b);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    assertOptimisticHead(block2b);
    assertStrictHead(block2b);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a, UInt64.ONE));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // And our head should switch
    assertOptimisticHead(block2a);
    assertStrictHead(block2a);
  }

  @Test
  void shouldConsiderWeightFromVotesForOptimisticBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1b, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(2, block2b, block1b);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    assertOptimisticHead(block2b);
    assertStrictHead(block1b);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a, UInt64.ONE));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // And our head should switch
    assertOptimisticHead(block2a);
    assertStrictHead(block1a);
  }

  @Test
  void shouldNotConsiderWeightFromVotesForInvalidBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1b, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(2, block2b, block1b);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    assertOptimisticHead(block2b);
    assertStrictHead(block1b);

    protoArray.markNodeInvalid(block2a);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a, UInt64.ONE));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // Votes for 2a don't count because it's invalid so we stick with chain b.
    assertOptimisticHead(block2b);
    assertStrictHead(block1b);
  }

  @Test
  void markNodeInvalid_shouldRemoveWeightWhenBlocksMarkedAsInvalid() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1b, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(2, block2b, block1b);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1b, UInt64.ZERO));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    assertOptimisticHead(block2b);
    assertStrictHead(block1b);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a, UInt64.ONE));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // We switch to chain a because it has the greater weight now
    assertOptimisticHead(block2a);
    assertStrictHead(block1a);

    // But oh no! It turns out to be invalid.
    protoArray.markNodeInvalid(block2a);

    // So we switch back to chain b (notably without having to applyScoreChanges)
    assertOptimisticHead(block2b);
    assertStrictHead(block1b);
  }

  @Test
  void markNodeValid_shouldConsiderAllAncestorsValidWhenMarkedAsValid() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3a));
    assertOptimisticHead(block3a);
    assertStrictHead(block1a);

    protoArray.markNodeValid(block3a);

    assertOptimisticHead(block3a);
    assertStrictHead(block3a);
  }

  @Test
  void contains_shouldContainValidBlock() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    assertThat(protoArray.contains(block1a)).isTrue();
  }

  @Test
  void contains_shouldContainOptimisticBlock() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    assertThat(protoArray.contains(block1a)).isTrue();
  }

  @Test
  void contains_shouldNotContainInvalidBlock() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.markNodeInvalid(block1a);
    assertThat(protoArray.contains(block1a)).isFalse();
  }

  @Test
  void contains_shouldNotContainDescendantsOfInvalidBlock() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(1, block2a, block1a);
    protoArray.markNodeInvalid(block1a);
    assertThat(protoArray.contains(block1a)).isFalse();
    assertThat(protoArray.contains(block2a)).isFalse();
  }

  private void assertOptimisticHead(final Bytes32 expectedBlockHash) {
    assertThat(
            protoArray.findOptimisticHead(GENESIS_CHECKPOINT.getRoot(), UInt64.ZERO, UInt64.ZERO))
        .isEqualTo(protoArray.getProtoNode(expectedBlockHash).orElseThrow());
  }

  private void assertStrictHead(final Bytes32 expectedBlockHash) {
    assertThat(protoArray.findHead(GENESIS_CHECKPOINT.getRoot(), UInt64.ZERO, UInt64.ZERO))
        .contains(protoArray.getProtoNode(expectedBlockHash).orElseThrow());
  }

  private void addValidBlock(final long slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {
    addOptimisticBlock(slot, blockRoot, parentRoot);
    protoArray.markNodeValid(blockRoot);
  }

  private void addOptimisticBlock(
      final long slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {
    protoArray.onBlock(
        UInt64.valueOf(slot),
        blockRoot,
        parentRoot,
        dataStructureUtil.randomBytes32(),
        GENESIS_CHECKPOINT.getEpoch(),
        GENESIS_CHECKPOINT.getEpoch(),
        Bytes32.ZERO,
        true);
  }

  private List<Long> computeDeltas() {
    final List<UInt64> balances =
        Collections.nCopies(voteUpdater.getHighestVotedValidatorIndex().intValue(), UInt64.ONE);
    return ProtoArrayScoreCalculator.computeDeltas(
        voteUpdater,
        protoArray.getTotalTrackedNodeCount(),
        protoArray::getIndexByRoot,
        balances,
        balances,
        Optional.empty(),
        Optional.empty(),
        UInt64.ZERO,
        UInt64.ZERO);
  }
}
