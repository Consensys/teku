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
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProposerWeighting;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ProtoArrayTest {
  private final Checkpoint GENESIS_CHECKPOINT = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(SpecFactory.createMinimal());
  private final VoteUpdater voteUpdater = mock(VoteUpdater.class);

  private final ProtoArray protoArray =
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
    assertThat(protoArray.findHead(GENESIS_CHECKPOINT.getRoot())).isEqualTo(blockRootB);

    protoArray.applyProposerWeighting(proposerWeightingA);
    assertThat(protoArray.findHead(GENESIS_CHECKPOINT.getRoot())).isEqualTo(blockRootA);
  }

  @Test
  void applyProposerWeighting_shouldIgnoreProposerWeightingForUnknownBlock() {
    protoArray.applyProposerWeighting(
        new ProposerWeighting(dataStructureUtil.randomBytes32(), UInt64.valueOf(500)));
    assertAllWeightsAreZero();
  }

  private void addBlock(final long slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {
    protoArray.onBlock(
        UInt64.valueOf(slot),
        blockRoot,
        parentRoot,
        dataStructureUtil.randomBytes32(),
        GENESIS_CHECKPOINT.getEpoch(),
        GENESIS_CHECKPOINT.getEpoch());
  }

  private void reverseProposerWeightings(final ProposerWeighting... weightings) {
    final List<Long> deltas =
        ProtoArrayScoreCalculator.computeDeltas(
            voteUpdater,
            protoArray.getTotalTrackedNodeCount(),
            protoArray.getIndices(),
            Collections.emptyList(),
            Collections.emptyList(),
            List.of(weightings));
    protoArray.applyScoreChanges(
        deltas, GENESIS_CHECKPOINT.getEpoch(), GENESIS_CHECKPOINT.getEpoch());
  }

  private void assertAllWeightsAreZero() {
    assertThat(protoArray.getNodes()).allMatch(node -> node.getWeight().isZero());
  }

  private ProtoNode getNode(final Bytes32 blockRoot) {
    return protoArray.getNodes().get(protoArray.getIndices().get(blockRoot));
  }
}
