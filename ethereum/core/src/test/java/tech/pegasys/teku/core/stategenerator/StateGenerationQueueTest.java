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

package tech.pegasys.teku.core.stategenerator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.stategenerator.StateGenerationQueue.RegenerationTask;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.metrics.TekuMetricCategory;

class StateGenerationQueueTest {

  private static final int ACTIVE_REGENERATION_LIMIT = 2;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final StateGenerationQueue stateGenerationQueue =
      new StateGenerationQueue(metricsSystem, () -> ACTIVE_REGENERATION_LIMIT);

  @BeforeEach
  void setUp() {
    stateGenerationQueue.startMetrics();
  }

  @Test
  void shouldGenerateBlockWhenItIsTheOnlyTask() {
    final StubRegenerationTask task = createRandomTask();
    final SafeFuture<SignedBlockAndState> result =
        stateGenerationQueue.regenerateStateForBlock(task);
    assertThat(result).isNotDone();
    task.assertRegeneratedWithoutRebase();

    final SignedBlockAndState expectedResult =
        dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    task.regenerationResult.complete(expectedResult);
    assertThat(result).isCompletedWithValue(expectedResult);
    assertAllRegenerationsComplete();
  }

  @Test
  void shouldRegenerateInParallelWhenLimitNotReached() {
    final StubRegenerationTask task1 = createRandomTask();
    final StubRegenerationTask task2 = createRandomTask();
    final StubRegenerationTask task3 = createRandomTask();
    final SafeFuture<SignedBlockAndState> result1 =
        stateGenerationQueue.regenerateStateForBlock(task1);
    final SafeFuture<SignedBlockAndState> result2 =
        stateGenerationQueue.regenerateStateForBlock(task2);
    final SafeFuture<SignedBlockAndState> result3 =
        stateGenerationQueue.regenerateStateForBlock(task3);
    assertThat(result1).isNotDone();
    assertThat(result2).isNotDone();
    assertThat(result3).isNotDone();
    task1.assertRegeneratedWithoutRebase();
    task2.assertRegeneratedWithoutRebase();
    task3.assertNotRegenerated();

    // Task 3 is queued until one of the previous tasks finishes
    task1.regenerationResult.complete(dataStructureUtil.randomSignedBlockAndState(UInt64.ONE));
    task3.assertRegeneratedWithoutRebase();
  }

  @Test
  void shouldUseQueuedRegenerationAsStartingPointIfPossible() {
    final SignedBlockAndState baseState = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final List<SignedBlockAndState> blocks =
        dataStructureUtil.randomSignedBlockAndStateSequence(baseState.getBlock(), 10, false);
    final SignedBlockAndState task1State = blocks.get(6);
    final List<SignedBlockAndState> task1Blocks = blocks.subList(0, 6);
    final HashTree task1Tree = createHashTreeForChain(task1Blocks);
    final StubRegenerationTask task1 = new StubRegenerationTask(task1State.getRoot(), task1Tree);
    final SafeFuture<SignedBlockAndState> result1 =
        stateGenerationQueue.regenerateStateForBlock(task1);
    assertThat(result1).isNotDone();
    task1.assertRegeneratedWithoutRebase();

    final Bytes32 task2Target = blocks.get(9).getRoot();
    final HashTree task2Tree = createHashTreeForChain(blocks);
    final StubRegenerationTask task2 = new StubRegenerationTask(task2Target, task2Tree);
    final SafeFuture<SignedBlockAndState> result2 =
        stateGenerationQueue.regenerateStateForBlock(task2);
    assertThat(result2).isNotDone();
    // Shouldn't start task 2 because it can use the result of task 1 as a better starting point
    task2.assertNotRegenerated();

    task1.regenerationResult.complete(task1State);
    assertThat(result1).isCompletedWithValue(task1State);

    task2.assertRegeneratedAfterRebase(task1State);
    final SignedBlockAndState task2State =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(2));
    task2.regenerationResult.complete(task2State);

    assertThat(result2).isCompletedWithValue(task2State);
    assertAllRegenerationsComplete();
  }

  @Test
  void shouldNotUseQueuedStartingPointBeforeTheCurrentRoot() {
    final SignedBlockAndState baseState = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final List<SignedBlockAndState> blocks =
        dataStructureUtil.randomSignedBlockAndStateSequence(baseState.getBlock(), 10, false);
    final SignedBlockAndState task1State = blocks.get(6);
    final List<SignedBlockAndState> task1Blocks = blocks.subList(0, 6);
    final HashTree task1Tree = createHashTreeForChain(task1Blocks);
    final StubRegenerationTask task1 = new StubRegenerationTask(task1State.getRoot(), task1Tree);
    final SafeFuture<SignedBlockAndState> result1 =
        stateGenerationQueue.regenerateStateForBlock(task1);
    assertThat(result1).isNotDone();
    task1.assertRegeneratedWithoutRebase();

    final Bytes32 task2Target = blocks.get(9).getRoot();
    final HashTree task2Tree = createHashTreeForChain(blocks.subList(7, 10));
    final StubRegenerationTask task2 = new StubRegenerationTask(task2Target, task2Tree);
    final SafeFuture<SignedBlockAndState> result2 =
        stateGenerationQueue.regenerateStateForBlock(task2);
    assertThat(result2).isNotDone();
    // Task 2 starts immediately because its base root is already better than task 1
    // even though task 1's target root is in the hash tree as the parent of its root
    task2.assertRegeneratedWithoutRebase();
  }

  private HashTree createHashTreeForChain(final List<SignedBlockAndState> blocks) {
    final HashTree.Builder builder = HashTree.builder();
    blocks.forEach(block -> builder.childAndParentRoots(block.getRoot(), block.getParentRoot()));
    builder.rootHash(blocks.get(0).getRoot());
    return builder.build();
  }

  private StubRegenerationTask createRandomTask() {
    final Bytes32 targetBlockRoot = dataStructureUtil.randomBytes32();
    final HashTree tree = createHashTree(targetBlockRoot);
    return new StubRegenerationTask(targetBlockRoot, tree);
  }

  private void assertAllRegenerationsComplete() {
    assertThat(
            metricsSystem.getGauge(TekuMetricCategory.BEACON, "regenerations_requested").getValue())
        .isZero();
    assertThat(metricsSystem.getGauge(TekuMetricCategory.BEACON, "regenerations_active").getValue())
        .isZero();
    assertThat(metricsSystem.getGauge(TekuMetricCategory.BEACON, "regenerations_queued").getValue())
        .isZero();
  }

  private HashTree createHashTree(final Bytes32 targetBlockRoot) {
    final HashTree.Builder hashTreeBuilder = HashTree.builder();
    Bytes32 childRoot = targetBlockRoot;
    Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    for (int i = 0; i < 3; i++) {
      hashTreeBuilder.childAndParentRoots(childRoot, parentRoot);
      childRoot = parentRoot;
      parentRoot = dataStructureUtil.randomBytes32();
    }
    hashTreeBuilder.childAndParentRoots(childRoot, parentRoot);
    hashTreeBuilder.rootHash(childRoot);
    return hashTreeBuilder.build();
  }

  private static class StubRegenerationTask extends RegenerationTask {

    private final SafeFuture<SignedBlockAndState> regenerationResult = new SafeFuture<>();
    private boolean regenerated = false;
    private Optional<SignedBlockAndState> rebasedTo = Optional.empty();

    public StubRegenerationTask(final Bytes32 blockRoot, final HashTree tree) {
      super(blockRoot, tree, null, null, null);
    }

    @Override
    public RegenerationTask rebase(final SignedBlockAndState newBaseBlockAndState) {
      rebasedTo = Optional.of(newBaseBlockAndState);
      return this;
    }

    @Override
    public SafeFuture<SignedBlockAndState> regenerate() {
      regenerated = true;
      return regenerationResult;
    }

    public void assertRegeneratedWithoutRebase() {
      assertThat(rebasedTo).isEmpty();
      assertThat(regenerated).isTrue();
    }

    public void assertNotRegenerated() {
      assertThat(regenerated).isFalse();
    }

    public void assertRegeneratedAfterRebase(final SignedBlockAndState newBaseState) {
      assertThat(rebasedTo).contains(newBaseState);
      assertThat(regenerated).isTrue();
      // Assert that rebase is valid
      final HashTree newTree = getTree().withRoot(rebasedTo.orElseThrow().getRoot()).build();
      assertThat(newTree.getRootHash()).isEqualTo(newBaseState.getRoot());
    }
  }
}
