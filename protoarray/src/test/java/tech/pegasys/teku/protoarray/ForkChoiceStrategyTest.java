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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
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
  private final ProtoArrayStorageChannel storageChannel = mock(ProtoArrayStorageChannel.class);

  @BeforeEach
  void setUp() {
    when(storageChannel.getProtoArraySnapshot())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

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
                    blockAndState.getState().getFinalized_checkpoint().getEpoch()));
    return ForkChoiceStrategy.initialize(protoArray);
  }

  @Test
  public void initialize_withLargeChain() {
    final MutableStore store = new TestStoreFactory().createGenesisStore();
    final int chainSize = 2_000;
    saveChainToStore(chainSize, store);
    final SafeFuture<ForkChoiceStrategy> future =
        ForkChoiceStrategy.initializeAndMigrateStorage(store, storageChannel);

    assertThat(future).isCompleted();
    final ForkChoiceStrategy forkChoiceStrategy = future.join();
    assertThat(forkChoiceStrategy.getTotalTrackedNodeCount()).isEqualTo(chainSize + 1);
  }

  @Test
  void initialize_shouldStoreProtoArraySnapshotToCompleteDataMigration() {
    final MutableStore store = new TestStoreFactory().createGenesisStore();
    final int chainSize = 5;
    saveChainToStore(chainSize, store);
    final SafeFuture<ForkChoiceStrategy> future =
        ForkChoiceStrategy.initializeAndMigrateStorage(store, storageChannel);

    assertThat(future).isCompleted();

    final ArgumentCaptor<ProtoArraySnapshot> captor =
        ArgumentCaptor.forClass(ProtoArraySnapshot.class);
    verify(storageChannel).onProtoArrayUpdate(captor.capture());

    final ProtoArraySnapshot snapshot = captor.getValue();
    assertThat(snapshot.getBlockInformationList()).hasSize(chainSize + 1);
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
    TestStoreImpl store = new TestStoreFactory().createAnchorStore(anchor);

    final SafeFuture<ForkChoiceStrategy> future =
        ForkChoiceStrategy.initializeAndMigrateStorage(store, storageChannel);
    assertThat(future).isCompleted();
    final ForkChoiceStrategy forkChoiceStrategy = future.join();

    assertThat(forkChoiceStrategy.getTotalTrackedNodeCount()).isEqualTo(1);
    final List<UInt64> effectiveBalances =
        dataStructureUtil
            .getSpec()
            .getBeaconStateUtil(anchor.getState().getSlot())
            .getEffectiveBalances(anchor.getState());
    final Bytes32 head =
        forkChoiceStrategy.applyPendingVotes(
            store, emptyList(), anchor.getCheckpoint(), anchor.getCheckpoint(), effectiveBalances);
    assertThat(head).isEqualTo(anchor.getRoot());
  }

  @Test
  void getAncestor_specifiedBlockIsAtSlot() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block = storageSystem.chainUpdater().addNewBestBlock();
    final ForkChoiceStrategy protoArrayStrategy = createProtoArray(storageSystem);
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
    final ForkChoiceStrategy strategy = createProtoArray(storageSystem);

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
    final ForkChoiceStrategy protoArrayStrategy = createProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(head.getRoot(), ancestor.getSlot()))
        .contains(ancestor.getRoot());
  }

  @Test
  void getChainHeads() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ForkChoiceStrategy protoArrayStrategy = createProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getChainHeads())
        .isEqualTo(Map.of(head.getBlock().getRoot(), head.getBlock().getSlot()));
  }

  @Test
  void getAncestor_headIsUnknown() {
    final StorageSystem storageSystem = initStorageSystem();
    final ForkChoiceStrategy protoArrayStrategy = createProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(dataStructureUtil.randomBytes32(), ZERO)).isEmpty();
  }

  @Test
  void getAncestor_noBlockAtSlot() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ForkChoiceStrategy protoArrayStrategy = createProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(head.getRoot(), ONE)).contains(head.getParentRoot());
  }

  @Test
  void applyTransaction_shouldNotContainRemovedBlocks() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block1 = storageSystem.chainUpdater().addNewBestBlock();
    final SignedBlockAndState block2 = storageSystem.chainUpdater().addNewBestBlock();

    final ForkChoiceStrategy strategy = createProtoArray(storageSystem);
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
    final ForkChoiceStrategy strategy = createProtoArray(storageSystem);

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

    final ForkChoiceStrategy strategy = createProtoArray(storageSystem);
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
    final ForkChoiceStrategy forkChoiceStrategy = createProtoArray(storageSystem);

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
    final ForkChoiceStrategy strategy = createProtoArray(storageSystem);
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
            emptyList(),
            storageSystem.recentChainData().getFinalizedCheckpoint().orElseThrow(),
            storageSystem.recentChainData().getStore().getBestJustifiedCheckpoint(),
            effectiveBalances);
    transaction.commit();

    assertThat(bestHead).isEqualTo(block4.getRoot());
  }

  private StorageSystem initStorageSystem() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    return storageSystem;
  }

  private ForkChoiceStrategy createProtoArray(final StorageSystem storageSystem) {
    final SafeFuture<ForkChoiceStrategy> future =
        ForkChoiceStrategy.initializeAndMigrateStorage(
            storageSystem.recentChainData().getStore(), storageSystem.createProtoArrayStorage());
    assertThat(future).isCompleted();
    return future.join();
  }

  private void saveChainToStore(final int blockCount, final MutableStore store) {
    final List<SignedBlockAndState> chain = generateChain(blockCount, store);
    chain.forEach(store::putBlockAndState);
  }

  // Creating mocks is much faster than generating random blocks via DataStructureUtil
  private List<SignedBlockAndState> generateChain(final int count, final MutableStore store) {
    final SignedBlockAndState genesis =
        store.retrieveBlockAndState(store.getFinalizedCheckpoint().getRoot()).join().orElseThrow();
    final List<SignedBlockAndState> chain = new ArrayList<>();

    final Checkpoint checkpoint = store.getFinalizedCheckpoint();
    SignedBlockAndState parent = genesis;
    for (int i = 0; i < count; i++) {
      final UInt64 slot = parent.getSlot().plus(ONE);
      final Bytes32 blockHash = Bytes32.fromHexStringLenient("0x" + i);
      final Bytes32 parentRoot = parent.getRoot();

      final SignedBeaconBlock block = mock(SignedBeaconBlock.class);
      when(block.getSlot()).thenReturn(slot);
      when(block.getRoot()).thenReturn(blockHash);
      when(block.hashTreeRoot()).thenReturn(blockHash);
      when(block.getParentRoot()).thenReturn(parentRoot);
      when(block.getStateRoot()).thenReturn(blockHash);

      final BeaconState state = mock(BeaconState.class);
      when(state.getSlot()).thenReturn(slot);
      when(state.hashTreeRoot()).thenReturn(blockHash);
      when(state.hashTreeRoot()).thenReturn(blockHash);
      when(state.getCurrent_justified_checkpoint()).thenReturn(checkpoint);
      when(state.getFinalized_checkpoint()).thenReturn(checkpoint);

      final SignedBlockAndState blockAndState = new SignedBlockAndState(block, state);
      chain.add(blockAndState);

      parent = blockAndState;
    }

    return chain;
  }
}
