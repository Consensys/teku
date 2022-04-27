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

package tech.pegasys.teku.ethereum.forkchoice;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreImpl;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;
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
            .finalizedCheckpoint(latestState.getFinalizedCheckpoint())
            .justifiedCheckpoint(latestState.getCurrentJustifiedCheckpoint())
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
                    blockAndState.getState().getCurrentJustifiedCheckpoint().getEpoch(),
                    blockAndState.getState().getFinalizedCheckpoint().getEpoch(),
                    blockAndState.getExecutionBlockHash().orElse(Bytes32.ZERO),
                    spec.isBlockProcessorOptimistic(blockAndState.getSlot())));
  }

  @Test
  void onPayloadExecution_shouldNotPenalizeNodeOnFailedExecution() {
    final ProtoArray protoArray = mock(ProtoArray.class);
    final ForkChoiceStrategy forkChoiceStrategy = ForkChoiceStrategy.initialize(spec, protoArray);
    forkChoiceStrategy.onExecutionPayloadResult(
        dataStructureUtil.randomBytes32(), PayloadStatus.failedExecution(new Error()));
    verify(protoArray, never()).markNodeInvalid(any(), any());
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
            .initialCheckpoint(Optional.of(anchor.getCheckpoint()))
            .justifiedCheckpoint(anchorState.getCurrentJustifiedCheckpoint())
            .finalizedCheckpoint(anchorState.getFinalizedCheckpoint())
            .build();
    protoArray.onBlock(
        anchor.getBlockSlot(),
        anchor.getRoot(),
        anchor.getParentRoot(),
        anchor.getStateRoot(),
        anchor.getEpoch(),
        anchor.getEpoch(),
        Bytes32.ZERO,
        false);
    final ForkChoiceStrategy forkChoiceStrategy = ForkChoiceStrategy.initialize(spec, protoArray);
    TestStoreImpl store = new TestStoreFactory().createAnchorStore(anchor);

    assertThat(forkChoiceStrategy.getTotalTrackedNodeCount()).isEqualTo(1);
    final List<UInt64> effectiveBalances =
        dataStructureUtil
            .getSpec()
            .getBeaconStateUtil(anchor.getState().getSlot())
            .getEffectiveBalances(anchor.getState());
    final Bytes32 head =
        forkChoiceStrategy.applyPendingVotes(
            store,
            Optional.empty(),
            anchor.getCheckpoint(),
            anchor.getCheckpoint(),
            effectiveBalances,
            ZERO,
            Collections.emptySet());
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
            BlockAndCheckpointEpochs.fromBlockAndState(bestBlock),
            BlockAndCheckpointEpochs.fromBlockAndState(forkBlock)),
        emptySet(),
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
                    false)));
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
        Set.of(block2.getRoot()),
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
            BlockAndCheckpointEpochs.fromBlockAndState(block1),
            BlockAndCheckpointEpochs.fromBlockAndState(block2)),
        emptySet(),
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
    strategy.applyUpdate(emptyList(), emptySet(), new Checkpoint(ONE, block2.getRoot()));
    assertThat(strategy.contains(block1.getRoot())).isTrue();
    assertThat(strategy.contains(block2.getRoot())).isTrue();
    assertThat(strategy.contains(block3.getRoot())).isTrue();
    assertThat(strategy.contains(block4.getRoot())).isTrue();

    // Prune when threshold is exceeded
    strategy.applyUpdate(emptyList(), emptySet(), new Checkpoint(ONE, block3.getRoot()));
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
    forkChoiceStrategy.applyUpdate(emptyList(), emptySet(), finalizedCheckpoint);

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
        Set.of(block1.getRoot(), block2.getRoot()),
        new Checkpoint(ONE, block3.getRoot()));

    assertThat(strategy.contains(block3.getRoot())).isTrue();
    assertThat(strategy.contains(block4.getRoot())).isTrue();

    final VoteUpdater transaction = storageSystem.recentChainData().startVoteUpdate();
    strategy.processAttestation(transaction, ZERO, block3.getRoot(), block3Epoch, false);

    final BeaconState block3State = block3.getState();

    final List<UInt64> effectiveBalances =
        dataStructureUtil
            .getSpec()
            .getBeaconStateUtil(block3State.getSlot())
            .getEffectiveBalances(block3State);
    final Bytes32 bestHead =
        strategy.applyPendingVotes(
            transaction,
            Optional.empty(),
            storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow(),
            storageSystem.recentChainData().getStore().getBestJustifiedCheckpoint(),
            effectiveBalances,
            ZERO,
            Collections.emptySet());
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
    strategy.processAttestation(transaction1, ZERO, block1.getRoot(), block1Epoch, false);
    transaction1.commit();

    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();
    final VoteUpdater transaction2 = storageSystem.recentChainData().startVoteUpdate();
    final UInt64 block2Epoch = spec.computeEpochAtSlot(block2.getSlot());
    strategy.processAttestation(transaction2, ZERO, block2.getRoot(), block2Epoch, true);

    final BeaconState block2State = block2.getState();
    final List<UInt64> effectiveBalances =
        dataStructureUtil
            .getSpec()
            .getBeaconStateUtil(block2State.getSlot())
            .getEffectiveBalances(block2State);
    final Set<UInt64> equivocatingIndices = new HashSet<>();
    equivocatingIndices.add(ZERO);
    final Bytes32 bestHead =
        strategy.applyPendingVotes(
            transaction2,
            Optional.empty(),
            storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow(),
            storageSystem.recentChainData().getStore().getBestJustifiedCheckpoint(),
            effectiveBalances,
            ZERO,
            equivocatingIndices);
    transaction2.commit();
    assertThat(bestHead).isEqualTo(block2.getRoot());

    assertThat(transaction2.getVote(ZERO).isEquivocated()).isTrue();
    // Not updated after equivocation
    assertThat(transaction2.getVote(ZERO).getNextRoot()).isEqualTo(block1.getRoot());
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
