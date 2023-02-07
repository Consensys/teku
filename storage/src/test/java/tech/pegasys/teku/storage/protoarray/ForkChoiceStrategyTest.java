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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreImpl;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class ForkChoiceStrategyTest extends AbstractBlockMetadataStoreTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Override
  protected BlockMetadataStore createBlockMetadataStore(
      final ChainBuilder chainBuilder, final ChainBuilder... additionalBuilders) {
    final BeaconState latestState = chainBuilder.getLatestBlockAndState().getState();
    final ProtoArray protoArray =
        ProtoArray.builder()
            .spec(spec)
            .currentEpoch(ZERO)
            .finalizedCheckpoint(latestState.getFinalizedCheckpoint())
            .justifiedCheckpoint(latestState.getCurrentJustifiedCheckpoint())
            .progressiveBalancesMode(spec.getGenesisSpecConfig().getProgressiveBalancesMode())
            .build();
    addBlocksFromBuilder(chainBuilder, protoArray);

    for (ChainBuilder builder : additionalBuilders) {
      addBlocksFromBuilder(builder, protoArray);
    }
    return ForkChoiceStrategy.initialize(spec, protoArray);
  }

  private void addBlocksFromBuilder(final ChainBuilder chainBuilder, final ProtoArray protoArray) {
    chainBuilder
        .streamBlocksAndStates()
        .filter(block -> !protoArray.contains(block.getRoot()))
        .forEach(
            blockAndState ->
                protoArray.onBlock(
                    blockAndState.getSlot(),
                    blockAndState.getRoot(),
                    blockAndState.getParentRoot(),
                    blockAndState.getStateRoot(),
                    spec.calculateBlockCheckpoints(blockAndState.getState()),
                    blockAndState.getExecutionBlockHash().orElse(Bytes32.ZERO),
                    spec.isBlockProcessorOptimistic(blockAndState.getSlot())));
  }

  @Test
  void onPayloadExecution_shouldNotPenalizeNodeOnFailedExecution() {
    final ProtoArray protoArray = mock(ProtoArray.class);
    final ForkChoiceStrategy forkChoiceStrategy = ForkChoiceStrategy.initialize(spec, protoArray);
    forkChoiceStrategy.onExecutionPayloadResult(
        dataStructureUtil.randomBytes32(), PayloadStatus.failedExecution(new Error()), true);
    verify(protoArray, never()).markNodeInvalid(any(), any());
    verify(protoArray, never()).markNodeValid(any());
  }

  @Test
  void onPayloadExecution_shouldPenalizeNodeOnInvalidExecutionWithoutTransition() {
    final ProtoArray protoArray = mock(ProtoArray.class);
    final ForkChoiceStrategy forkChoiceStrategy = ForkChoiceStrategy.initialize(spec, protoArray);
    forkChoiceStrategy.onExecutionPayloadResult(
        dataStructureUtil.randomBytes32(),
        PayloadStatus.invalid(Optional.of(dataStructureUtil.randomBytes32()), Optional.empty()),
        false);
    verify(protoArray, times(1)).markParentChainInvalid(any(), any());
    verify(protoArray, never()).markNodeValid(any());
  }

  @Test
  public void findHead_worksForChainInitializedFromNonGenesisAnchor() {
    // Set up store with an anchor point that has justified and finalized checkpoints prior to its
    // epoch
    final UInt64 initialEpoch = UInt64.valueOf(100);
    final BeaconState anchorState =
        dataStructureUtil
            .stateBuilderPhase0()
            .setJustifiedCheckpointsToEpoch(initialEpoch.minus(2))
            .setFinalizedCheckpointToEpoch(initialEpoch.minus(3))
            .setSlotToStartOfEpoch(initialEpoch)
            .build();
    AnchorPoint anchor = dataStructureUtil.createAnchorFromState(anchorState);
    final ProtoArray protoArray =
        ProtoArray.builder()
            .spec(spec)
            .initialCheckpoint(Optional.of(anchor.getCheckpoint()))
            .currentEpoch(anchor.getEpoch())
            .justifiedCheckpoint(anchorState.getCurrentJustifiedCheckpoint())
            .finalizedCheckpoint(anchorState.getFinalizedCheckpoint())
            .progressiveBalancesMode(spec.getGenesisSpecConfig().getProgressiveBalancesMode())
            .build();
    protoArray.onBlock(
        anchor.getBlockSlot(),
        anchor.getRoot(),
        anchor.getParentRoot(),
        anchor.getStateRoot(),
        new BlockCheckpoints(
            anchor.getCheckpoint(),
            anchor.getCheckpoint(),
            anchor.getCheckpoint(),
            anchor.getCheckpoint()),
        Bytes32.ZERO,
        false);
    final ForkChoiceStrategy forkChoiceStrategy = ForkChoiceStrategy.initialize(spec, protoArray);
    TestStoreImpl store = new TestStoreFactory().createAnchorStore(anchor);

    assertThat(forkChoiceStrategy.getTotalTrackedNodeCount()).isEqualTo(1);
    final List<UInt64> effectiveBalances =
        dataStructureUtil
            .getSpec()
            .getBeaconStateUtil(anchor.getState().getSlot())
            .getEffectiveActiveUnslashedBalances(anchor.getState());
    final Bytes32 head =
        forkChoiceStrategy.applyPendingVotes(
            store,
            Optional.empty(),
            spec.getCurrentSlot(store),
            anchor.getCheckpoint(),
            anchor.getCheckpoint(),
            effectiveBalances,
            ZERO);
    assertThat(head).isEqualTo(anchor.getRoot());
  }

  @Test
  void getAncestor_specifiedBlockIsAtSlot() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block = storageSystem.chainUpdater().addNewBestBlock();
    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(block.getRoot(), block.getSlot()))
        .contains(block.getRoot());
  }

  @Test
  void getBlockRootsAtSlot_shouldReturnAllRoots() {
    final StorageSystem storageSystem = initStorageSystem();
    storageSystem.chainUpdater().addNewBestBlock();
    ChainBuilder fork = storageSystem.chainBuilder().fork();
    final SignedBlockAndState bestBlock = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState forkBlock =
        fork.generateBlockAtSlot(
            bestBlock.getSlot(),
            ChainBuilder.BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);

    strategy.applyUpdate(
        List.of(
            BlockAndCheckpoints.fromBlockAndState(spec, bestBlock),
            BlockAndCheckpoints.fromBlockAndState(spec, forkBlock)),
        emptySet(),
        emptyMap(),
        storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow());
    assertThat(strategy.getBlockRootsAtSlot(bestBlock.getSlot()))
        .containsExactlyInAnyOrder(bestBlock.getRoot(), forkBlock.getRoot());
  }

  @Test
  void getAncestor_ancestorIsFound() {
    final StorageSystem storageSystem = initStorageSystem();
    storageSystem.chainUpdater().advanceChain(1);
    final SignedBlockAndState ancestor = storageSystem.chainUpdater().advanceChain(2);
    storageSystem.chainUpdater().advanceChain(3);
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(head.getRoot(), ancestor.getSlot()))
        .contains(ancestor.getRoot());
  }

  @Test
  void getChainHeads() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getChainHeads())
        .isEqualTo(
            List.of(
                new ProtoNodeData(
                    head.getSlot(),
                    head.getRoot(),
                    head.getParentRoot(),
                    head.getStateRoot(),
                    head.getExecutionBlockHash().orElse(Bytes32.ZERO),
                    ProtoNodeValidationStatus.VALID,
                    spec.calculateBlockCheckpoints(head.getState()),
                    ZERO)));
  }

  @Test
  void getAncestor_headIsUnknown() {
    final StorageSystem storageSystem = initStorageSystem();
    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(dataStructureUtil.randomBytes32(), ZERO)).isEmpty();
  }

  @Test
  void getAncestor_noBlockAtSlot() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(head.getRoot(), ONE)).contains(head.getParentRoot());
  }

  @Test
  void applyTransaction_shouldNotContainRemovedBlocks() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();

    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);
    strategy.applyUpdate(
        emptyList(),
        emptySet(),
        Map.of(block2.getRoot(), block2.getSlot()),
        storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow());

    assertThat(strategy.contains(block1.getRoot())).isTrue();
    assertThat(strategy.contains(block2.getRoot())).isFalse();
  }

  @Test
  void applyTransaction_shouldAddNewBlocks() {
    final StorageSystem storageSystem = initStorageSystem();
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);

    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();

    strategy.applyUpdate(
        List.of(
            BlockAndCheckpoints.fromBlockAndState(spec, block1),
            BlockAndCheckpoints.fromBlockAndState(spec, block2)),
        emptySet(),
        emptyMap(),
        storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow());

    assertThat(strategy.contains(block1.getRoot())).isTrue();
    assertThat(strategy.contains(block2.getRoot())).isTrue();
  }

  @Test
  void applyTransaction_shouldPruneWhenFinalizedCheckpointExceedsPruningThreshold() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block3 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block4 = storageSystem.chainUpdater().addNewBestBlock();

    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);
    // Genesis = 0, block1 = 1, block2 = 2, block3 = 3, block4 = 4
    strategy.setPruneThreshold(3);

    // Not pruned because threshold isn't reached.
    strategy.applyUpdate(
        emptyList(), emptySet(), emptyMap(), new Checkpoint(ONE, block2.getRoot()));
    assertThat(strategy.contains(block1.getRoot())).isTrue();
    assertThat(strategy.contains(block2.getRoot())).isTrue();
    assertThat(strategy.contains(block3.getRoot())).isTrue();
    assertThat(strategy.contains(block4.getRoot())).isTrue();

    // Prune when threshold is exceeded
    strategy.applyUpdate(
        emptyList(), emptySet(), emptyMap(), new Checkpoint(ONE, block3.getRoot()));
    assertThat(strategy.contains(block1.getRoot())).isFalse();
    assertThat(strategy.contains(block2.getRoot())).isFalse();
    assertThat(strategy.contains(block3.getRoot())).isTrue();
    assertThat(strategy.contains(block4.getRoot())).isTrue();
  }

  @Test
  void applyUpdate_shouldPruneBlocksPriorToLatestFinalized() {
    final StorageSystem storageSystem = initStorageSystem();
    storageSystem.chainUpdater().advanceChainUntil(10);
    final ForkChoiceStrategy forkChoiceStrategy = getProtoArray(storageSystem);

    final SignedBeaconBlock finalizedBlock = storageSystem.chainBuilder().getBlockAtSlot(4);
    final Checkpoint finalizedCheckpoint = new Checkpoint(UInt64.ONE, finalizedBlock.getRoot());
    forkChoiceStrategy.setPruneThreshold(0);
    forkChoiceStrategy.applyUpdate(emptyList(), emptySet(), emptyMap(), finalizedCheckpoint);

    // Check that all blocks prior to latest finalized have been pruned
    final List<SignedBlockAndState> allBlocks =
        storageSystem.chainBuilder().streamBlocksAndStates().collect(Collectors.toList());
    for (SignedBlockAndState block : allBlocks) {
      if (block.getSlot().isLessThan(finalizedBlock.getSlot())) {
        assertThat(forkChoiceStrategy.contains(block.getRoot())).isFalse();
        assertThat(forkChoiceStrategy.blockSlot(block.getRoot())).isEmpty();
        assertThat(forkChoiceStrategy.blockParentRoot(block.getRoot())).isEmpty();
      } else {
        assertThat(forkChoiceStrategy.contains(block.getRoot())).isTrue();
        assertThat(forkChoiceStrategy.blockSlot(block.getRoot())).contains(block.getSlot());
        assertThat(forkChoiceStrategy.blockParentRoot(block.getRoot()))
            .contains(block.getParentRoot());
      }
    }
  }

  @Test
  void applyScoreChanges_shouldWorkAfterRemovingNodes() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block3 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block4 = storageSystem.chainUpdater().addNewBestBlock();
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);
    final UInt64 block3Epoch = spec.computeEpochAtSlot(block3.getSlot());

    strategy.applyUpdate(
        emptyList(),
        emptySet(),
        Map.of(block1.getRoot(), block1.getSlot(), block2.getRoot(), block2.getSlot()),
        new Checkpoint(ONE, block3.getRoot()));

    assertThat(strategy.contains(block3.getRoot())).isTrue();
    assertThat(strategy.contains(block4.getRoot())).isTrue();

    final VoteUpdater transaction = storageSystem.recentChainData().startVoteUpdate();
    strategy.processAttestation(transaction, ZERO, block3.getRoot(), block3Epoch);

    final BeaconState block3State = block3.getState();

    final List<UInt64> effectiveBalances =
        dataStructureUtil
            .getSpec()
            .getBeaconStateUtil(block3State.getSlot())
            .getEffectiveActiveUnslashedBalances(block3State);
    final Bytes32 bestHead =
        strategy.applyPendingVotes(
            transaction,
            Optional.empty(),
            storageSystem.recentChainData().getCurrentEpoch().orElseThrow(),
            storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow(),
            storageSystem.recentChainData().getStore().getBestJustifiedCheckpoint(),
            effectiveBalances,
            ZERO);
    transaction.commit();

    assertThat(bestHead).isEqualTo(block4.getRoot());
  }

  @Test
  void executionBlockHash_shouldBeEmptyForUnknownBlock() {
    final StorageSystem storageSystem = initStorageSystem(TestSpecFactory.createMinimalBellatrix());
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);
    assertThat(strategy.executionBlockHash(Bytes32.ZERO)).isEmpty();
  }

  @Test
  void executionBlockHash_shouldGetExecutionRootForKnownBlock() {
    final StorageSystem storageSystem = initStorageSystem(TestSpecFactory.createMinimalBellatrix());
    final SignedBlockAndState block1 =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(1, BlockOptions.create().setTransactions());
    storageSystem.chainUpdater().saveBlock(block1);
    assertThat(block1.getExecutionBlockHash()).isNotEmpty();

    final ReadOnlyForkChoiceStrategy strategy =
        storageSystem.recentChainData().getForkChoiceStrategy().orElseThrow();
    assertThat(strategy.executionBlockHash(block1.getRoot()))
        .isEqualTo(block1.getExecutionBlockHash());

    storageSystem.restarted();
    assertThat(
            storageSystem
                .recentChainData()
                .getForkChoiceStrategy()
                .orElseThrow()
                .executionBlockHash(block1.getRoot()))
        .isEqualTo(block1.getExecutionBlockHash());
  }

  @Test
  void applyPendingVotes_shouldMarkEquivocation() {
    final StorageSystem storageSystem = initStorageSystem();
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);

    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final VoteUpdater transaction1 = storageSystem.recentChainData().startVoteUpdate();
    final UInt64 block1Epoch = spec.computeEpochAtSlot(block1.getSlot());
    strategy.processAttestation(transaction1, ZERO, block1.getRoot(), block1Epoch);
    transaction1.commit();

    // Mark our Validator as going to be equivocated like when AttesterSlashing received
    final VoteUpdater transaction2 = storageSystem.recentChainData().startVoteUpdate();
    VoteTracker voteTracker = transaction2.getVote(ZERO);
    transaction2.putVote(ZERO, voteTracker.createNextEquivocating());
    transaction2.commit();

    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();
    final VoteUpdater transaction3 = storageSystem.recentChainData().startVoteUpdate();
    final UInt64 block2Epoch = spec.computeEpochAtSlot(block2.getSlot());
    strategy.processAttestation(transaction3, ZERO, block2.getRoot(), block2Epoch);

    final BeaconState block2State = block2.getState();
    final List<UInt64> effectiveBalances =
        dataStructureUtil
            .getSpec()
            .getBeaconStateUtil(block2State.getSlot())
            .getEffectiveActiveUnslashedBalances(block2State);
    final Bytes32 bestHead =
        strategy.applyPendingVotes(
            transaction3,
            Optional.empty(),
            storageSystem.recentChainData().getCurrentEpoch().orElseThrow(),
            storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow(),
            storageSystem.recentChainData().getStore().getBestJustifiedCheckpoint(),
            effectiveBalances,
            ZERO);
    transaction3.commit();
    assertThat(bestHead).isEqualTo(block2.getRoot());

    assertThat(transaction3.getVote(ZERO).isCurrentEquivocating()).isTrue();
    // Not updated after equivocation
    assertThat(transaction3.getVote(ZERO).getNextRoot()).isEqualTo(block1.getRoot());
  }

  @Test
  void shouldConsiderHeadOptimisticWhenItIsNotViable() {
    // If we optimistically import blocks which include enough attestations to update the justified
    // checkpoint, then later invalidate some non-justified blocks such that the head block no
    // longer includes enough attestations to support that justification, then none of our blocks
    // will be viable because they won't have the same checkpoints as our store.
    // In that case we will use the justified checkpoint as chain head and should remain in
    // optimistic sync mode

    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    chainUpdater.initializeGenesisWithPayload(
        true, dataStructureUtil.randomExecutionPayloadHeader());
    final ForkChoiceStrategy protoArray = getProtoArray(storageSystem);
    final RecentChainData recentChainData = storageSystem.recentChainData();
    final SignedBlockAndState optimisticHead = chainUpdater.finalizeCurrentChainOptimistically();
    ProtoNodeData firstJustifiedData =
        protoArray.getBlockData(optimisticHead.getRoot()).orElseThrow();
    ProtoNodeData lastUnjustifiedData =
        protoArray.getBlockData(firstJustifiedData.getParentRoot()).orElseThrow();
    final Checkpoint currentJustified = recentChainData.getJustifiedCheckpoint().orElseThrow();
    while (lastUnjustifiedData.getCheckpoints().getJustifiedCheckpoint().equals(currentJustified)) {
      firstJustifiedData = lastUnjustifiedData;
      lastUnjustifiedData =
          protoArray.getBlockData(lastUnjustifiedData.getParentRoot()).orElseThrow();
    }
    assertThat(firstJustifiedData.getCheckpoints().getJustifiedCheckpoint())
        .isEqualTo(currentJustified);
    assertThat(lastUnjustifiedData.getCheckpoints().getJustifiedCheckpoint())
        .isNotEqualTo(currentJustified);

    // Invalidate blocks that updated the justified checkpoint
    protoArray.onExecutionPayloadResult(
        firstJustifiedData.getRoot(),
        PayloadStatus.invalid(Optional.empty(), Optional.of("No good, very bad block")),
        true);
    // And validate the justified checkpoint
    protoArray.onExecutionPayloadResult(currentJustified.getRoot(), PayloadStatus.VALID, false);

    // Find new chain head
    final SlotAndBlockRoot revertHead =
        protoArray.findHead(
            recentChainData.getCurrentEpoch().orElseThrow(),
            recentChainData.getJustifiedCheckpoint().orElseThrow(),
            recentChainData.getFinalizedCheckpoint().orElseThrow());
    recentChainData.updateHead(revertHead.getBlockRoot(), optimisticHead.getSlot());

    final ForkChoiceState forkChoiceState =
        protoArray.getForkChoiceState(
            recentChainData.getCurrentEpoch().orElseThrow(),
            recentChainData.getJustifiedCheckpoint().orElseThrow(),
            recentChainData.getFinalizedCheckpoint().orElseThrow());

    // Should have reverted to the justified checkpoint as head
    assertThat(forkChoiceState.getHeadBlockRoot()).isEqualTo(currentJustified.getRoot());
    // The current head block itself is fully validated
    assertThat(protoArray.isFullyValidated(currentJustified.getRoot())).isTrue();
    // But we consider the chain head optimistic because of the updated justified checkpoint
    assertThat(forkChoiceState.isHeadOptimistic()).isTrue();
  }

  private StorageSystem initStorageSystem() {
    return initStorageSystem(spec);
  }

  private StorageSystem initStorageSystem(final Spec spec) {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    return storageSystem;
  }

  private ForkChoiceStrategy getProtoArray(final StorageSystem storageSystem) {
    return storageSystem.recentChainData().getStore().getForkChoiceStrategy();
  }
}
