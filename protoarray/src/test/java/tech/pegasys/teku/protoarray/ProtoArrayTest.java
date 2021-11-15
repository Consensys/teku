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
import static tech.pegasys.teku.protoarray.ProtoNodeValidationStatus.VALID;

import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProposerWeighting;
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

  private ProtoArray protoArray =
      new ProtoArrayBuilder()
          .justifiedCheckpoint(GENESIS_CHECKPOINT)
          .finalizedCheckpoint(GENESIS_CHECKPOINT)
          .build();

  @BeforeEach
  void setUp() {
    addBlock(0, Bytes32.ZERO, Bytes32.ZERO);
  }

  @Test
  void applyProposerWeighting_shouldApplyAndReverseProposerWeightingToNodeAndDescendants() {
    final Bytes32 block1A = dataStructureUtil.randomBytes32();
    final Bytes32 block2A = dataStructureUtil.randomBytes32();
    final Bytes32 block3A = dataStructureUtil.randomBytes32();
    final Bytes32 block2B = dataStructureUtil.randomBytes32();
    final Bytes32 block3B = dataStructureUtil.randomBytes32();
    final Bytes32 block3C = dataStructureUtil.randomBytes32();
    final ProposerWeighting proposerWeighting = new ProposerWeighting(block3A, UInt64.valueOf(500));

    addBlock(1, block1A, Bytes32.ZERO);
    addBlock(1, block2A, block1A);
    addBlock(1, block2B, block1A);
    addBlock(1, block3B, block2B);
    addBlock(1, block3C, block2A);
    addBlock(1, block3A, block2A);
    protoArray.applyProposerWeighting(proposerWeighting);

    assertThat(getNode(block3A).getWeight()).isEqualTo(proposerWeighting.getWeight());
    assertThat(getNode(block2A).getWeight()).isEqualTo(proposerWeighting.getWeight());
    assertThat(getNode(block1A).getWeight()).isEqualTo(proposerWeighting.getWeight());

    assertThat(getNode(block2B).getWeight()).isEqualTo(UInt64.ZERO);
    assertThat(getNode(block3B).getWeight()).isEqualTo(UInt64.ZERO);
    assertThat(getNode(block3C).getWeight()).isEqualTo(UInt64.ZERO);

    reverseProposerWeightings(proposerWeighting);

    assertAllWeightsAreZero();
  }

  @Test
  void applyProposerWeighting_shouldAffectSelectedHead() {
    final Bytes32 blockRootA = dataStructureUtil.randomBytes32();
    final Bytes32 blockRootB = dataStructureUtil.randomBytes32();
    final ProposerWeighting proposerWeightingA =
        new ProposerWeighting(blockRootA, UInt64.valueOf(500));
    final ProposerWeighting proposerWeightingB =
        new ProposerWeighting(blockRootB, UInt64.valueOf(80));

    addBlock(1, blockRootA, Bytes32.ZERO);
    addBlock(1, blockRootB, Bytes32.ZERO);

    protoArray.applyProposerWeighting(proposerWeightingB);
    assertThat(
            protoArray
                .findHead(
                    GENESIS_CHECKPOINT.getRoot(),
                    GENESIS_CHECKPOINT.getEpoch(),
                    GENESIS_CHECKPOINT.getEpoch())
                .getBlockRoot())
        .isEqualTo(blockRootB);

    protoArray.applyProposerWeighting(proposerWeightingA);
    assertThat(
            protoArray
                .findHead(
                    GENESIS_CHECKPOINT.getRoot(),
                    GENESIS_CHECKPOINT.getEpoch(),
                    GENESIS_CHECKPOINT.getEpoch())
                .getBlockRoot())
        .isEqualTo(blockRootA);
  }

  @Test
  void applyProposerWeighting_shouldIgnoreProposerWeightingForUnknownBlock() {
    protoArray.applyProposerWeighting(
        new ProposerWeighting(dataStructureUtil.randomBytes32(), UInt64.valueOf(500)));
    assertAllWeightsAreZero();
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
    addBlock(12, justifiedRoot, dataStructureUtil.randomBytes32());
    final ProtoNode head = protoArray.findHead(justifiedRoot, UInt64.ONE, UInt64.ONE);
    assertThat(head).isEqualTo(protoArray.getProtoNode(justifiedRoot).orElseThrow());
  }

  @Test
  void findHead_shouldExcludeOptimisticBlocks() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2Hash, block1Hash, OPTIMISTIC);

    assertStrictHead(block1Hash);
  }

  @Test
  void findHead_shouldIncludeValidatedBlocks() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2Hash, block1Hash, VALID);

    assertStrictHead(block2Hash);
  }

  @Test
  void findHead_shouldExcludeBlocksWithInvalidPayload() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2Hash, block1Hash, OPTIMISTIC);

    assertStrictHead(block1Hash);

    protoArray.markNodeInvalid(block2Hash);

    assertStrictHead(block1Hash);
  }

  @Test
  void findHead_shouldExcludeBlocksDescendedFromAnInvalidBlock() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block3Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2Hash, block1Hash, OPTIMISTIC);
    addBlock(3, block3Hash, block2Hash, OPTIMISTIC);

    assertStrictHead(block1Hash);

    protoArray.markNodeInvalid(block2Hash);

    assertStrictHead(block1Hash);
  }

  @Test
  void findHead_shouldThrowFatalServiceFailureExceptionWhenAllBlocksAreInvalid() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block3Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), OPTIMISTIC);
    addBlock(2, block2Hash, block1Hash, OPTIMISTIC);
    addBlock(3, block3Hash, block2Hash, OPTIMISTIC);
    protoArray.markNodeInvalid(GENESIS_CHECKPOINT.getRoot());

    assertThatThrownBy(() -> assertStrictHead(block1Hash))
        .isInstanceOf(FatalServiceFailureException.class);
  }

  /**
   * This isn't actually the right behaviour. During an optimistic sync it's quite likely that the
   * finalized block will only be optimistically sync'd so we'll ultimately need to have a way for
   * ProtoArray to indicate it doesn't have a strict head available and fallback to a finalized
   * block as the strict chain head (possibly the initial anchor block).
   */
  @Test
  void findHead_shouldThrowFatalServiceExceptionWhenAllBlocksInChainAreOptimistic() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block3Hash = dataStructureUtil.randomBytes32();
    protoArray
        .getProtoNode(GENESIS_CHECKPOINT.getRoot())
        .orElseThrow()
        .setValidationStatus(OPTIMISTIC);
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), OPTIMISTIC);
    addBlock(2, block2Hash, block1Hash, OPTIMISTIC);
    addBlock(3, block3Hash, block2Hash, OPTIMISTIC);

    assertThatThrownBy(() -> assertStrictHead(block1Hash))
        .isInstanceOf(FatalServiceFailureException.class);
  }

  @Test
  void findOptimisticHead_shouldIncludeOptimisticBlocks() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2Hash, block1Hash, OPTIMISTIC);

    assertOptimisticHead(block2Hash);
  }

  @Test
  void findOptimisticHead_shouldIncludeValidBlocks() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2Hash, block1Hash, VALID);

    final ProtoNode head =
        protoArray.findOptimisticHead(GENESIS_CHECKPOINT.getRoot(), UInt64.ZERO, UInt64.ZERO);
    assertThat(head).isEqualTo(protoArray.getProtoNode(block2Hash).orElseThrow());
  }

  @Test
  void findOptimisticHead_shouldExcludeInvalidBlocks() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2Hash, block1Hash, OPTIMISTIC);

    assertOptimisticHead(block2Hash);

    protoArray.markNodeInvalid(block2Hash);

    assertOptimisticHead(block1Hash);
  }

  @Test
  void findOptimisticHead_shouldExcludeBlocksDescendedFromAnInvalidBlock() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block3Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2Hash, block1Hash, OPTIMISTIC);
    addBlock(3, block3Hash, block2Hash, OPTIMISTIC);
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1Hash).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3Hash));
    assertOptimisticHead(block3Hash);

    protoArray.markNodeInvalid(block2Hash);

    assertOptimisticHead(block1Hash);
  }

  @Test
  void findOptimisticHead_shouldThrowExceptionWhenAllBlocksAreInvalid() {
    // ProtoArray always contains the finalized block, if it's invalid there's a big problem.
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block3Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), OPTIMISTIC);
    addBlock(2, block2Hash, block1Hash, OPTIMISTIC);
    addBlock(3, block3Hash, block2Hash, OPTIMISTIC);

    assertOptimisticHead(block3Hash);

    protoArray.markNodeInvalid(GENESIS_CHECKPOINT.getRoot());

    assertThatThrownBy(() -> assertOptimisticHead(block1Hash))
        .isInstanceOf(FatalServiceFailureException.class);
  }

  @Test
  void shouldConsiderWeightFromVotesForValidBlocks() {
    final Bytes32 block1aHash = dataStructureUtil.randomBytes32();
    final Bytes32 block1bHash = dataStructureUtil.randomBytes32();
    final Bytes32 block2aHash = dataStructureUtil.randomBytes32();
    final Bytes32 block2bHash = dataStructureUtil.randomBytes32();
    addBlock(1, block1aHash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(1, block1bHash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2aHash, block1aHash, VALID);
    addBlock(2, block2bHash, block1bHash, VALID);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    assertOptimisticHead(block2bHash);
    assertStrictHead(block2bHash);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1bHash, block2aHash, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1bHash, block2aHash, UInt64.ONE));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // And our head should switch
    assertOptimisticHead(block2aHash);
    assertStrictHead(block2aHash);
  }

  @Test
  void shouldConsiderWeightFromVotesForOptimisticBlocks() {
    final Bytes32 block1aHash = dataStructureUtil.randomBytes32();
    final Bytes32 block1bHash = dataStructureUtil.randomBytes32();
    final Bytes32 block2aHash = dataStructureUtil.randomBytes32();
    final Bytes32 block2bHash = dataStructureUtil.randomBytes32();
    addBlock(1, block1aHash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(1, block1bHash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2aHash, block1aHash, OPTIMISTIC);
    addBlock(2, block2bHash, block1bHash, OPTIMISTIC);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    assertOptimisticHead(block2bHash);
    assertStrictHead(block1bHash);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1bHash, block2aHash, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1bHash, block2aHash, UInt64.ONE));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // And our head should switch
    assertOptimisticHead(block2aHash);
    assertStrictHead(block1aHash);
  }

  @Test
  void shouldNotConsiderWeightFromVotesForInvalidBlocks() {
    final Bytes32 block1aHash = dataStructureUtil.randomBytes32();
    final Bytes32 block1bHash = dataStructureUtil.randomBytes32();
    final Bytes32 block2aHash = dataStructureUtil.randomBytes32();
    final Bytes32 block2bHash = dataStructureUtil.randomBytes32();
    addBlock(1, block1aHash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(1, block1bHash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2aHash, block1aHash, OPTIMISTIC);
    addBlock(2, block2bHash, block1bHash, OPTIMISTIC);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    assertOptimisticHead(block2bHash);
    assertStrictHead(block1bHash);

    protoArray.markNodeInvalid(block2aHash);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1bHash, block2aHash, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1bHash, block2aHash, UInt64.ONE));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // Votes for 2a don't count because it's invalid so we stick with chain b.
    assertOptimisticHead(block2bHash);
    assertStrictHead(block1bHash);
  }

  @Test
  void updateValidity_shouldRemoveWeightWhenBlocksMarkedAsInvalid() {
    final Bytes32 block1aHash = dataStructureUtil.randomBytes32();
    final Bytes32 block1bHash = dataStructureUtil.randomBytes32();
    final Bytes32 block2aHash = dataStructureUtil.randomBytes32();
    final Bytes32 block2bHash = dataStructureUtil.randomBytes32();
    addBlock(1, block1aHash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(1, block1bHash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2aHash, block1aHash, OPTIMISTIC);
    addBlock(2, block2bHash, block1bHash, OPTIMISTIC);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1bHash, UInt64.ZERO));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    assertOptimisticHead(block2bHash);
    assertStrictHead(block1bHash);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1bHash, block2aHash, UInt64.ONE));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1bHash, block2aHash, UInt64.ONE));
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // We switch to chain a because it has the greater weight now
    assertOptimisticHead(block2aHash);
    assertStrictHead(block1aHash);

    // But oh no! It turns out to be invalid.
    protoArray.markNodeInvalid(block2aHash);

    // So we switch back to chain b (notably without having to applyScoreChanges)
    assertOptimisticHead(block2bHash);
    assertStrictHead(block1bHash);
  }

  @Test
  void updateValidity_shouldConsiderAllAncestorsValidWhenMarkedAsValid() {
    final Bytes32 block1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block2Hash = dataStructureUtil.randomBytes32();
    final Bytes32 block3Hash = dataStructureUtil.randomBytes32();
    addBlock(1, block1Hash, GENESIS_CHECKPOINT.getRoot(), VALID);
    addBlock(2, block2Hash, block1Hash, OPTIMISTIC);
    addBlock(3, block3Hash, block2Hash, OPTIMISTIC);
    protoArray.applyScoreChanges(computeDeltas(), UInt64.ZERO, UInt64.ZERO);

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1Hash).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3Hash));
    assertOptimisticHead(block3Hash);
    assertStrictHead(block1Hash);

    protoArray.markNodeValid(block3Hash);

    assertOptimisticHead(block3Hash);
    assertStrictHead(block3Hash);
  }

  private void assertOptimisticHead(final Bytes32 expectedBlockHash) {
    assertThat(
            protoArray.findOptimisticHead(GENESIS_CHECKPOINT.getRoot(), UInt64.ZERO, UInt64.ZERO))
        .isEqualTo(protoArray.getProtoNode(expectedBlockHash).orElseThrow());
  }

  private void assertStrictHead(final Bytes32 expectedBlockHash) {
    assertThat(protoArray.findHead(GENESIS_CHECKPOINT.getRoot(), UInt64.ZERO, UInt64.ZERO))
        .isEqualTo(protoArray.getProtoNode(expectedBlockHash).orElseThrow());
  }

  private void addBlock(final long slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {
    addBlock(slot, blockRoot, parentRoot, VALID);
  }

  private void addBlock(
      final long slot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final ProtoNodeValidationStatus validationStatus) {
    protoArray.onBlock(
        UInt64.valueOf(slot),
        blockRoot,
        parentRoot,
        dataStructureUtil.randomBytes32(),
        GENESIS_CHECKPOINT.getEpoch(),
        GENESIS_CHECKPOINT.getEpoch(),
        validationStatus);
  }

  private void reverseProposerWeightings(final ProposerWeighting... weightings) {
    final List<Long> deltas =
        ProtoArrayScoreCalculator.computeDeltas(
            voteUpdater,
            protoArray.getTotalTrackedNodeCount(),
            protoArray::getIndexByRoot,
            Collections.emptyList(),
            Collections.emptyList(),
            List.of(weightings));
    protoArray.applyScoreChanges(
        deltas, GENESIS_CHECKPOINT.getEpoch(), GENESIS_CHECKPOINT.getEpoch());
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
        Collections.emptyList());
  }

  private void assertAllWeightsAreZero() {
    assertThat(protoArray.getNodes()).allMatch(node -> node.getWeight().isZero());
  }

  private ProtoNode getNode(final Bytes32 blockRoot) {
    return protoArray.getNodes().get(protoArray.getRootIndices().get(blockRoot));
  }
}
