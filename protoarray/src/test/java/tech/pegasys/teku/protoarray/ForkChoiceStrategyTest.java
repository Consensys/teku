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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Map;
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
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreImpl;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class ForkChoiceStrategyTest extends AbstractBlockMetadataStoreTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Override
  protected BlockMetadataStore createBlockMetadataStore(final ChainBuilder chainBuilder) {
    final BeaconState latestState = chainBuilder.getLatestBlockAndState().getState();
    final ProtoArray protoArray =
        ProtoArray.builder()
            .finalizedCheckpoint(latestState.getFinalized_checkpoint())
            .justifiedCheckpoint(latestState.getCurrent_justified_checkpoint())
            .build();
    chainBuilder
        .streamBlocksAndStates()
        .forEach(
            blockAndState ->
                protoArray.onBlock(
                    blockAndState.getSlot(),
                    blockAndState.getRoot(),
                    blockAndState.getParentRoot(),
                    blockAndState.getStateRoot(),
                    blockAndState.getState().getCurrent_justified_checkpoint().getEpoch(),
                    blockAndState.getState().getFinalized_checkpoint().getEpoch(),
                    blockAndState.getExecutionBlockHash().orElse(Bytes32.ZERO),
                    spec.isBlockProcessorOptimistic(blockAndState.getSlot())));
    return ForkChoiceStrategy.initialize(spec, protoArray);
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
            .justifiedCheckpoint(anchorState.getCurrent_justified_checkpoint())
            .finalizedCheckpoint(anchorState.getFinalized_checkpoint())
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
        .isEqualTo(Map.of(head.getBlock().getRoot(), head.getBlock().getSlot()));
  }

  @Test
  void getOptimisticChainHeads_shouldOnlyReturnOptimisticNodes() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState commonAncestor = storageSystem.chainUpdater().advanceChain(5);
    final ChainBuilder optimisticFork = storageSystem.chainBuilder().fork();
    final SignedBlockAndState optimisticHead =
        optimisticFork.generateBlockAtSlot(commonAncestor.getSlot().plus(1));
    storageSystem.chainUpdater().saveOptimisticBlock(optimisticHead);

    // Add a fully validated fork head that shouldn't be returned
    final SignedBlockAndState validHead = storageSystem.chainUpdater().advanceChain(7);

    final ForkChoiceStrategy protoArrayStrategy = getProtoArray(storageSystem);

    assertThat(protoArrayStrategy.getOptimisticChainHeads())
        .containsOnly(entry(optimisticHead.getRoot(), optimisticHead.getSlot()));
    assertThat(protoArrayStrategy.getChainHeads())
        .containsOnly(
            entry(optimisticHead.getRoot(), optimisticHead.getSlot()),
            entry(validHead.getRoot(), validHead.getSlot()));
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
    strategy.processAttestation(transaction, ZERO, block3.getRoot(), block3Epoch);

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
            ZERO);
    transaction.commit();

    assertThat(bestHead).isEqualTo(block4.getRoot());
  }

  @Test
  void executionBlockHash_shouldBeEmptyForUnknownBlock() {
    final StorageSystem storageSystem = initStorageSystem(TestSpecFactory.createMinimalMerge());
    final ForkChoiceStrategy strategy = getProtoArray(storageSystem);
    assertThat(strategy.executionBlockHash(Bytes32.ZERO)).isEmpty();
  }

  @Test
  void executionBlockHash_shouldGetExecutionRootForKnownBlock() {
    final StorageSystem storageSystem = initStorageSystem(TestSpecFactory.createMinimalMerge());
    final SignedBlockAndState block1 =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(1, BlockOptions.create().setTransactions());
    storageSystem.chainUpdater().saveBlock(block1);
    assertThat(block1.getExecutionBlockHash()).isNotEmpty();

    final ForkChoiceStrategy strategy =
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
