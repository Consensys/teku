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

package tech.pegasys.teku.dataproviders.generators;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.ExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.SignedExecutionPayloadAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.datastructures.state.BlockRootAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

@TestSpecContext(milestone = {SpecMilestone.PHASE0, SpecMilestone.GLOAS})
class StateGenerationTaskTest {

  private static final int REPLAY_TOLERANCE_TO_AVOID_LOADING_IN_EPOCHS = 0;
  private Spec spec;
  private ChainBuilder chainBuilder;
  private TrackingBlockProvider blockProvider;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    chainBuilder = ChainBuilder.create(spec);
    chainBuilder.generateGenesis();
    blockProvider = new TrackingBlockProvider(getBlockProvider());
  }

  @TestTemplate
  void performTask_shouldRegenerateState() {
    chainBuilder.generateBlocksUpToSlot(3);
    final StateGenerationTask task = createTask(0, 3);
    final SafeFuture<Optional<StateAndBlockSummary>> result = task.performTask();
    assertThatSafeFuture(result).isCompletedWithOptionalContaining(getExpectedBlockAndState(3));
    assertRequestedBlockRangeInclusive(1, 3);
  }

  @TestTemplate
  void performTask_shouldLoadStateFromLatestEpochBoundary() {
    chainBuilder.generateBlocksUpToSlot(5);
    final SignedBeaconBlock epochBoundaryBlock = chainBuilder.getBlockAtSlot(3);
    final Optional<SlotAndBlockRoot> epochBoundaryRoot =
        Optional.of(
            new SlotAndBlockRoot(epochBoundaryBlock.getSlot(), epochBoundaryBlock.getRoot()));
    final StateGenerationTask task = createTask(1, 5, epochBoundaryRoot);
    final SafeFuture<Optional<StateAndBlockSummary>> result = task.performTask();

    assertThatSafeFuture(result).isCompletedWithOptionalContaining(getExpectedBlockAndState(5));
    assertRequestedBlockRangeInclusive(4, 5);
  }

  @TestTemplate
  void rebase_shouldStartFromMoreRecentState() {
    chainBuilder.generateBlocksUpToSlot(5);

    final StateGenerationTask originalTask = createTask(0, 5);

    final StateGenerationTask rebasedTask = originalTask.rebase(getExpectedBlockAndState(4));
    final SafeFuture<Optional<StateAndBlockSummary>> result = rebasedTask.performTask();
    assertThatSafeFuture(result).isCompletedWithOptionalContaining(getExpectedBlockAndState(5));
    assertRequestedBlockRangeInclusive(5, 5);
  }

  @TestTemplate
  void streamIntermediateSteps_shouldStreamParentBlocksUpToInitialStartingPoint() {
    chainBuilder.generateBlocksUpToSlot(5);

    final List<Bytes32> expectedIntermediateSteps =
        chainBuilder
            .streamBlocksAndStates(3, 4)
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList());
    Collections.reverse(expectedIntermediateSteps);

    final StateGenerationTask task = createTask(3, 5);
    final Stream<Bytes32> intermediateSteps = task.streamIntermediateSteps();
    assertThat(intermediateSteps).containsExactlyElementsOf(expectedIntermediateSteps);
  }

  @TestTemplate
  void performTask_shouldReplayExecutionPayloadsDuringMultiBlockRegeneration(
      final SpecContext specContext) {
    specContext.assumeGloasActive();
    chainBuilder.generateBlocksUpToSlot(5);

    // Replaying 5 blocks from genesis would fail with "Bid is not for the right parent block"
    // without execution payload replay, because latestBlockHash goes stale
    final StateGenerationTask task =
        createTask(0, 5, Optional.empty(), getExecutionPayloadProvider());

    final SafeFuture<Optional<StateAndBlockSummary>> result = task.performTask();
    assertThat(result).isCompleted();
    assertThat(result.join()).isPresent();
    assertThat(result.join().orElseThrow().getSlot()).isEqualTo(UInt64.valueOf(5));
  }

  /**
   * Returns the expected block-and-state after regeneration. For GLOAS, state regeneration includes
   * execution payload processing, so the expected state is the post-execution-payload state.
   */
  private SignedBlockAndState getExpectedBlockAndState(final int slot) {
    final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(slot);
    return chainBuilder
        .getExecutionPayloadStateAtSlot(UInt64.valueOf(slot))
        .map(epState -> new SignedBlockAndState(blockAndState.getBlock(), epState))
        .orElse(blockAndState);
  }

  private void assertRequestedBlockRangeInclusive(final int fromSlot, final int toSlot) {
    assertThat(blockProvider.getRequestedBlocks())
        .containsExactlyInAnyOrderElementsOf(
            chainBuilder
                .streamBlocksAndStates(fromSlot, toSlot)
                .map(SignedBlockAndState::getRoot)
                .collect(Collectors.toSet()));
  }

  private StateGenerationTask createTask(final int startSlot, final int endSlot) {
    return createTask(startSlot, endSlot, Optional.empty());
  }

  private StateGenerationTask createTask(
      final int startSlot, final int endSlot, final Optional<SlotAndBlockRoot> epochBoundaryRoot) {
    return createTask(startSlot, endSlot, epochBoundaryRoot, getExecutionPayloadProvider());
  }

  private StateGenerationTask createTask(
      final int startSlot,
      final int endSlot,
      final Optional<SlotAndBlockRoot> epochBoundaryRoot,
      final ExecutionPayloadProvider executionPayloadProvider) {
    final SignedBlockAndState startBlockAndState = chainBuilder.getBlockAndStateAtSlot(startSlot);
    final SignedBeaconBlock endBlock = chainBuilder.getBlockAtSlot(endSlot);
    final HashTree.Builder treeBuilder =
        HashTree.builder()
            .block(startBlockAndState.getBlock())
            .rootHash(startBlockAndState.getRoot());
    SignedBeaconBlock block = endBlock;
    while (block.getSlot().isGreaterThan(startBlockAndState.getSlot())) {
      treeBuilder.block(block);
      block = chainBuilder.getBlock(block.getParentRoot()).orElseThrow();
    }
    final HashTree tree = treeBuilder.build();
    return new StateGenerationTask(
        spec,
        endBlock.getRoot(),
        tree,
        blockProvider,
        executionPayloadProvider,
        new StateRegenerationBaseSelector(
            spec,
            epochBoundaryRoot,
            () ->
                Optional.of(
                    new BlockRootAndState(
                        startBlockAndState.getRoot(), startBlockAndState.getState())),
            getStateAndBlockProvider(),
            Optional.empty(),
            REPLAY_TOLERANCE_TO_AVOID_LOADING_IN_EPOCHS));
  }

  private ExecutionPayloadProvider getExecutionPayloadProvider() {
    final Map<Bytes32, SignedExecutionPayloadEnvelope> executionPayloadMap =
        chainBuilder
            .streamExecutionPayloadsAndStates(0, chainBuilder.getLatestSlot().intValue())
            .collect(
                Collectors.toMap(
                    SignedExecutionPayloadAndState::getBeaconBlockRoot,
                    SignedExecutionPayloadAndState::executionPayload));
    return ExecutionPayloadProvider.fromDynamicMap(executionPayloadMap);
  }

  private static class TrackingBlockProvider implements BlockProvider {
    private final Set<Bytes32> requestedBlocks = new HashSet<>();
    private final BlockProvider delegate;

    private TrackingBlockProvider(final BlockProvider delegate) {
      this.delegate = delegate;
    }

    @Override
    public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getBlocks(final Set<Bytes32> blockRoots) {
      requestedBlocks.addAll(blockRoots);
      return delegate.getBlocks(blockRoots);
    }

    public Set<Bytes32> getRequestedBlocks() {
      return requestedBlocks;
    }
  }

  private BlockProvider getBlockProvider() {
    return BlockProvider.fromDynamicMap(
        () ->
            chainBuilder
                .streamBlocksAndStates()
                .collect(
                    Collectors.toMap(
                        StateAndBlockSummary::getRoot, SignedBlockAndState::getBlock)));
  }

  private StateAndBlockSummaryProvider getStateAndBlockProvider() {
    return blockRoot ->
        SafeFuture.completedFuture(
            chainBuilder
                .getBlockAndState(blockRoot)
                .map(
                    blockAndState ->
                        chainBuilder
                            .getExecutionPayloadStateAtSlot(blockAndState.getSlot())
                            .map(
                                epState ->
                                    (StateAndBlockSummary)
                                        new SignedBlockAndState(blockAndState.getBlock(), epState))
                            .orElse(blockAndState)));
  }
}
