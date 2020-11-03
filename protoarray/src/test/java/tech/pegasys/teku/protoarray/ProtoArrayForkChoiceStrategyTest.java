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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class ProtoArrayForkChoiceStrategyTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ProtoArrayStorageChannel storageChannel = mock(ProtoArrayStorageChannel.class);

  @BeforeEach
  void setUp() {
    when(storageChannel.getProtoArraySnapshot())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  public void initialize_withLargeChain() {
    final MutableStore store = new TestStoreFactory().createGenesisStore();
    final int chainSize = 2_000;
    saveChainToStore(chainSize, store);
    final SafeFuture<ProtoArrayForkChoiceStrategy> future =
        ProtoArrayForkChoiceStrategy.initialize(store, storageChannel);

    assertThat(future).isCompleted();
    final ProtoArrayForkChoiceStrategy forkChoiceStrategy = future.join();
    assertThat(forkChoiceStrategy.size()).isEqualTo(chainSize + 1);
  }

  @Test
  void initialize_shouldStoreProtoArraySnapshotToCompleteDataMigration() {
    final MutableStore store = new TestStoreFactory().createGenesisStore();
    final int chainSize = 5;
    saveChainToStore(chainSize, store);
    final SafeFuture<ProtoArrayForkChoiceStrategy> future =
        ProtoArrayForkChoiceStrategy.initialize(store, storageChannel);

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
            .stateBuilder()
            .setJustifiedCheckpointsToEpoch(initialEpoch.minus(2))
            .setFinalizedCheckpointToEpoch(initialEpoch.minus(3))
            .setSlotToStartOfEpoch(initialEpoch)
            .build();
    AnchorPoint anchor = dataStructureUtil.createAnchorFromState(anchorState);
    MutableStore store = new TestStoreFactory().createAnchorStore(anchor);

    final SafeFuture<ProtoArrayForkChoiceStrategy> future =
        ProtoArrayForkChoiceStrategy.initialize(store, storageChannel);
    assertThat(future).isCompleted();
    final ProtoArrayForkChoiceStrategy forkChoiceStrategy = future.join();

    assertThat(forkChoiceStrategy.size()).isEqualTo(1);
    final Bytes32 head =
        forkChoiceStrategy.findHead(
            store, anchor.getCheckpoint(), anchor.getCheckpoint(), anchor.getState());
    assertThat(head).isEqualTo(anchor.getRoot());
  }

  @Test
  void getAncestor_specifiedBlockIsAtSlot() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState block = storageSystem.chainUpdater().addNewBestBlock();
    final ProtoArrayForkChoiceStrategy protoArrayStrategy = createProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(block.getRoot(), block.getSlot()))
        .contains(block.getRoot());
  }

  @Test
  void getAncestor_ancestorIsFound() {
    final StorageSystem storageSystem = initStorageSystem();
    storageSystem.chainUpdater().advanceChain(1);
    final SignedBlockAndState ancestor = storageSystem.chainUpdater().advanceChain(2);
    storageSystem.chainUpdater().advanceChain(3);
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ProtoArrayForkChoiceStrategy protoArrayStrategy = createProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(head.getRoot(), ancestor.getSlot()))
        .contains(ancestor.getRoot());
  }

  @Test
  void getAncestor_headIsUnknown() {
    final StorageSystem storageSystem = initStorageSystem();
    final ProtoArrayForkChoiceStrategy protoArrayStrategy = createProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(dataStructureUtil.randomBytes32(), ZERO)).isEmpty();
  }

  @Test
  void getAncestor_noBlockAtSlot() {
    final StorageSystem storageSystem = initStorageSystem();
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(5);
    final ProtoArrayForkChoiceStrategy protoArrayStrategy = createProtoArray(storageSystem);
    assertThat(protoArrayStrategy.getAncestor(head.getRoot(), ONE)).contains(head.getParentRoot());
  }

  private StorageSystem initStorageSystem() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    storageSystem.chainUpdater().initializeGenesis();
    return storageSystem;
  }

  private ProtoArrayForkChoiceStrategy createProtoArray(final StorageSystem storageSystem) {
    final SafeFuture<ProtoArrayForkChoiceStrategy> future =
        ProtoArrayForkChoiceStrategy.initialize(
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
      when(block.hash_tree_root()).thenReturn(blockHash);
      when(block.getParent_root()).thenReturn(parentRoot);
      when(block.getStateRoot()).thenReturn(blockHash);

      final BeaconState state = mock(BeaconState.class);
      when(state.getSlot()).thenReturn(slot);
      when(state.hash_tree_root()).thenReturn(blockHash);
      when(state.getCurrent_justified_checkpoint()).thenReturn(checkpoint);
      when(state.getFinalized_checkpoint()).thenReturn(checkpoint);

      final SignedBlockAndState blockAndState = new SignedBlockAndState(block, state);
      chain.add(blockAndState);

      parent = blockAndState;
    }

    return chain;
  }
}
