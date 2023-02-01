/*
 * Copyright ConsenSys Software Inc., 2022
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.ProgressiveBalancesMode;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.StubVoteUpdater;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ProtoArrayTest {
  private static final Checkpoint GENESIS_CHECKPOINT = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalPhase0());
  private final VoteUpdater voteUpdater = new StubVoteUpdater();
  private final StatusLogger statusLog = mock(StatusLogger.class);

  private final Bytes32 block1a = dataStructureUtil.randomBytes32();
  private final Bytes32 block1b = dataStructureUtil.randomBytes32();
  private final Bytes32 block2a = dataStructureUtil.randomBytes32();
  private final Bytes32 block2b = dataStructureUtil.randomBytes32();
  private final Bytes32 block3a = dataStructureUtil.randomBytes32();
  private final Bytes32 block4a = dataStructureUtil.randomBytes32();

  private ProtoArray protoArray =
      new ProtoArrayBuilder()
          .spec(dataStructureUtil.getSpec())
          .statusLog(statusLog)
          .currentEpoch(ZERO)
          .justifiedCheckpoint(GENESIS_CHECKPOINT)
          .finalizedCheckpoint(GENESIS_CHECKPOINT)
          .progressiveBalancesMode(ProgressiveBalancesMode.FULL)
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
    final Checkpoint justifiedCheckpoint = new Checkpoint(UInt64.ONE, justifiedRoot);
    final Checkpoint finalizedCheckpoint = new Checkpoint(UInt64.ONE, justifiedRoot);
    protoArray =
        new ProtoArrayBuilder()
            .spec(dataStructureUtil.getSpec())
            .currentEpoch(ZERO)
            .justifiedCheckpoint(justifiedCheckpoint)
            .finalizedCheckpoint(finalizedCheckpoint)
            .initialEpoch(UInt64.ZERO)
            .progressiveBalancesMode(ProgressiveBalancesMode.FULL)
            .build();
    // Justified block will have justified and finalized epoch of 0 which doesn't match the current
    // so would normally be not viable, but we should allow it anyway.
    addValidBlock(12, justifiedRoot, dataStructureUtil.randomBytes32());
    final ProtoNode head =
        protoArray.findOptimisticHead(UInt64.valueOf(5), justifiedCheckpoint, finalizedCheckpoint);
    assertThat(head).isEqualTo(protoArray.getProtoNode(justifiedRoot).orElseThrow());
  }

  @Test
  void findOptimisticHead_shouldIncludeOptimisticBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);

    assertHead(block2a);
  }

  @Test
  void findOptimisticHead_shouldIncludeValidBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(2, block2a, block1a);

    assertHead(block2a);
  }

  @Test
  void findOptimisticHead_shouldExcludeInvalidBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);

    assertHead(block2a);

    protoArray.markNodeInvalid(block2a, Optional.empty());

    assertHead(block1a);
  }

  @Test
  void findOptimisticHead_shouldExcludeBlocksDescendedFromAnInvalidBlock() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    // Apply score changes to ensure that the best descendant index is updated
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3a));
    assertHead(block3a);

    protoArray.markNodeInvalid(block2a, Optional.empty());

    assertHead(block1a);
  }

  @Test
  void findOptimisticHead_shouldAcceptOptimisticBlocksWithInvalidAncestors() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    // Apply score changes to ensure that the best descendant index is updated
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3a));
    assertHead(block3a);

    protoArray.markNodeInvalid(block3a, Optional.empty());

    assertHead(block2a);
  }

  @Test
  void findOptimisticHead_shouldThrowExceptionWhenAllBlocksAreInvalid() {
    // ProtoArray always contains the finalized block, if it's invalid there's a big problem.
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markNodeInvalid(GENESIS_CHECKPOINT.getRoot(), Optional.empty());

    assertThatThrownBy(
            () ->
                protoArray.findOptimisticHead(
                    UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT))
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
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    assertHead(block2b);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a, UInt64.ONE));
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    // And our head should switch
    assertHead(block2a);
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
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    assertHead(block2b);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a, UInt64.ONE));
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    // And our head should switch
    assertHead(block2a);
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
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    assertHead(block2b);

    protoArray.markNodeInvalid(block2a, Optional.empty());

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a, UInt64.ONE));
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    // Votes for 2a don't count because it's invalid so we stick with chain b.
    assertHead(block2b);
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
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    assertHead(block2b);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a, UInt64.ONE));
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    // We switch to chain a because it has the greater weight now
    assertHead(block2a);

    // But oh no! It turns out to be invalid.
    protoArray.markNodeInvalid(block2a, Optional.empty());

    // So we switch back to chain b (notably without having to applyScoreChanges)
    assertHead(block2b);
  }

  @Test
  void markNodeInvalid_shouldMarkAllNodesAfterLastValidHashAsInvalid() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markNodeInvalid(block3a, Optional.of(getExecutionBlockHash(block1a)));

    assertHead(block1a);
    assertThat(protoArray.contains(block3a)).isFalse();
    assertThat(protoArray.contains(block2a)).isFalse();
  }

  @Test
  void markNodeInvalid_shouldLeaveLastValidBlockAndAncestorsAsOptimistic() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    addOptimisticBlock(4, block4a, block3a);

    assertHead(block4a);

    protoArray.markNodeInvalid(block4a, Optional.of(getExecutionBlockHash(block2a)));

    assertHead(block2a);
    assertThat(protoArray.contains(block4a)).isFalse();
    assertThat(protoArray.contains(block3a)).isFalse();
    assertThat(protoArray.contains(block2a)).isTrue();
    assertThat(protoArray.contains(block1a)).isTrue();
    assertThat(protoArray.getProtoNode(block1a).map(ProtoNode::isOptimistic)).contains(true);
    assertThat(protoArray.getProtoNode(block2a).map(ProtoNode::isOptimistic)).contains(true);
  }

  @Test
  void markNodeInvalid_shouldNotMarkAncestorsValidWhenLastValidBlockNotProvided() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markNodeInvalid(block3a, Optional.empty());

    assertHead(block2a);
    assertThat(protoArray.contains(block3a)).isFalse();
    assertThat(protoArray.contains(block2a)).isTrue();
    assertThat(protoArray.contains(block1a)).isTrue();
    assertThat(protoArray.getProtoNode(block1a).map(ProtoNode::isOptimistic)).contains(true);
    assertThat(protoArray.getProtoNode(block2a).map(ProtoNode::isOptimistic)).contains(true);
  }

  @Test
  void markNodeInvalid_shouldOnlyMarkSpecifiedNodeAsInvalidWhenLastValidHashUnknown() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markNodeInvalid(block3a, Optional.of(dataStructureUtil.randomBytes32()));

    assertHead(block2a);
    assertThat(protoArray.contains(block3a)).isFalse();
    assertThat(protoArray.contains(block2a)).isTrue();
    assertThat(protoArray.contains(block1a)).isTrue();
    assertThat(protoArray.getProtoNode(block1a).map(ProtoNode::isOptimistic)).contains(true);
    assertThat(protoArray.getProtoNode(block2a).map(ProtoNode::isOptimistic)).contains(true);
  }

  @Test
  void markNodeValid_shouldConsiderAllAncestorsValidWhenMarkedAsValid() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    protoArray.applyScoreChanges(
        computeDeltas(), UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3a));
    assertHead(block3a);

    protoArray.markNodeValid(block3a);

    assertHead(block3a);
  }

  @Test
  void findAndMarkInvalidChain_shouldNotDoAnythingWhenLatestValidHashFromHead() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markParentChainInvalid(block3a, Optional.of(getExecutionBlockHash(block3a)));

    assertHead(block3a);
    assertThat(protoArray.contains(block3a)).isTrue();
    verifyNoInteractions(statusLog);
  }

  @Test
  void findAndMarkInvalidChain_shouldNotDoAnythingWhenLatestValidHashNodeNotFound() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markParentChainInvalid(block3a, Optional.of(dataStructureUtil.randomBytes32()));

    assertHead(block3a);
    assertThat(protoArray.contains(block3a)).isTrue();
  }

  @Test
  void findAndMarkInvalidChain_shouldMarkAllNodesAfterLatestValidHashAsInvalid() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markParentChainInvalid(block3a, Optional.of(getExecutionBlockHash(block1a)));

    assertHead(block1a);
    assertThat(protoArray.contains(block3a)).isFalse();
    assertThat(protoArray.contains(block2a)).isFalse();
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
    protoArray.markNodeInvalid(block1a, Optional.empty());
    assertThat(protoArray.contains(block1a)).isFalse();
  }

  @Test
  void contains_shouldNotContainDescendantsOfInvalidBlock() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(1, block2a, block1a);
    protoArray.markNodeInvalid(block1a, Optional.empty());
    assertThat(protoArray.contains(block1a)).isFalse();
    assertThat(protoArray.contains(block2a)).isFalse();
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_emptyWhenHeadNotKnown() {
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block1a)).isEmpty();
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_emptyWhenTransitionNotYetHappened() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block1a)).isEmpty();
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_emptyWhenTransitionBeforeStartOfProtoArray() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block3a)).isEmpty();
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_emptyWhenAfterSpecifiedHead() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addOptimisticBlock(2, block2a, block1a, Bytes32.ZERO);
    addOptimisticBlock(3, block3a, block2a, Bytes32.ZERO);
    addOptimisticBlock(4, block4a, block3a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block3a)).isEmpty();
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_transitionBlockIsHead() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addOptimisticBlock(2, block2a, block1a, Bytes32.ZERO);
    addOptimisticBlock(3, block3a, block2a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block3a))
        .isEqualTo(protoArray.getProtoNode(block3a));
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_transitionBlockIsParent() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block3a))
        .isEqualTo(protoArray.getProtoNode(block2a));
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_transitionBlockIsDescendant() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    addOptimisticBlock(4, block4a, block3a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block4a))
        .isEqualTo(protoArray.getProtoNode(block2a));
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_transitionBlockIsFullyValidated() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addValidBlock(2, block2a, block1a);
    addValidBlock(3, block3a, block2a);
    addValidBlock(4, block4a, block3a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block4a)).isEmpty();
  }

  @Test
  void onBlock_shouldNotMarkDefaultPayloadAsOptimistic() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addOptimisticBlock(1, block1b, GENESIS_CHECKPOINT.getRoot(), dataStructureUtil.randomBytes32());

    assertThat(protoArray.getProtoNode(block1a).orElseThrow().isOptimistic()).isFalse();
    assertThat(protoArray.getProtoNode(block1b).orElseThrow().isOptimistic()).isTrue();
  }

  private void assertHead(final Bytes32 expectedBlockHash) {
    final ProtoNode node = protoArray.getProtoNode(expectedBlockHash).orElseThrow();
    assertThat(
            protoArray.findOptimisticHead(
                UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT))
        .isEqualTo(node);

    assertThat(node.getBestDescendantIndex()).isEmpty();
    assertThat(node.getBestChildIndex()).isEmpty();
  }

  private void addValidBlock(final long slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {
    addValidBlock(slot, blockRoot, parentRoot, getExecutionBlockHash(blockRoot));
  }

  private void addValidBlock(
      final long slot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 executionBlockHash) {
    addOptimisticBlock(slot, blockRoot, parentRoot, executionBlockHash);
    protoArray.markNodeValid(blockRoot);
  }

  private void addOptimisticBlock(
      final long slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {
    final Bytes32 executionBlockHash = getExecutionBlockHash(blockRoot);
    addOptimisticBlock(slot, blockRoot, parentRoot, executionBlockHash);
  }

  private void addOptimisticBlock(
      final long slot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 executionBlockHash) {
    protoArray.onBlock(
        UInt64.valueOf(slot),
        blockRoot,
        parentRoot,
        dataStructureUtil.randomBytes32(),
        new BlockCheckpoints(
            GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT),
        executionBlockHash,
        true);
  }

  private Bytes32 getExecutionBlockHash(final Bytes32 blockRoot) {
    return Hash.sha256(blockRoot);
  }

  private LongList computeDeltas() {
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
