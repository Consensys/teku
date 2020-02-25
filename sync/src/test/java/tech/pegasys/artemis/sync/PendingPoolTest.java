/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.events.FinalizedCheckpointEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;

public class PendingPoolTest {
  private final EventBus eventBus = new EventBus();
  private final UnsignedLong historicalTolerance = UnsignedLong.valueOf(5);
  private final UnsignedLong futureTolerance = UnsignedLong.valueOf(2);
  private final PendingPool<SignedBeaconBlock> pendingPool =
      PendingPool.createForBlocks(eventBus, historicalTolerance, futureTolerance);
  private UnsignedLong currentSlot = historicalTolerance.times(UnsignedLong.valueOf(2));
  private List<Bytes32> requiredRootEvents = new ArrayList<>();
  private List<Bytes32> requiredRootDroppedEvents = new ArrayList<>();

  @BeforeEach
  public void setup() {
    // Set up slot
    assertThat(pendingPool.start()).isCompleted();
    pendingPool.subscribeRequiredBlockRoot(requiredRootEvents::add);
    pendingPool.subscribeRequiredBlockRootDropped(requiredRootDroppedEvents::add);
    setSlot(currentSlot);
  }

  private void setSlot(final long slot) {
    setSlot(UnsignedLong.valueOf(slot));
  }

  private void setSlot(final UnsignedLong slot) {
    currentSlot = slot;
    eventBus.post(new SlotEvent(slot));
  }

  @AfterEach
  public void cleanup() {
    assertThat(pendingPool.stop()).isCompleted();
  }

  @Test
  public void add_blockForCurrentSlot() {
    final SignedBeaconBlock block =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(block.getParent_root(), false))
        .containsExactlyInAnyOrder(block);
    assertThat(requiredRootEvents).containsExactly(block.getParent_root());
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_historicalBlockWithinWindow() {
    final UnsignedLong slot = currentSlot.minus(historicalTolerance);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), 1);
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(block.getParent_root(), false))
        .containsExactlyInAnyOrder(block);
    assertThat(requiredRootEvents).containsExactly(block.getParent_root());
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_historicalBlockOutsideWindow() {
    final UnsignedLong slot = currentSlot.minus(historicalTolerance).minus(UnsignedLong.ONE);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), 1);
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(0);
    assertThat(pendingPool.getItemsDependingOn(block.getParent_root(), false)).isEmpty();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_futureBlockWithinWindow() {
    final UnsignedLong slot = currentSlot.plus(futureTolerance);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), 1);
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(block.getParent_root(), false))
        .containsExactlyInAnyOrder(block);
    assertThat(requiredRootEvents).containsExactly(block.getParent_root());
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_futureBlockOutsideWindow() {
    final UnsignedLong slot = currentSlot.plus(futureTolerance).plus(UnsignedLong.ONE);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), 1);
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(0);
    assertThat(pendingPool.getItemsDependingOn(block.getParent_root(), false)).isEmpty();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_nonFinalizedBlock() {
    final SignedBeaconBlock finalizedBlock = DataStructureUtil.randomSignedBeaconBlock(10, 1);
    final Checkpoint checkpoint = finalizedCheckpoint(finalizedBlock);
    eventBus.post(new FinalizedCheckpointEvent(checkpoint));

    final UnsignedLong slot = checkpoint.getEpochSlot().plus(UnsignedLong.ONE);
    setSlot(slot);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), 1);

    pendingPool.add(block);
    assertThat(pendingPool.contains(block)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(block.getParent_root(), false))
        .containsExactlyInAnyOrder(block);
    assertThat(requiredRootEvents).containsExactly(block.getParent_root());
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_finalizedBlock() {
    final SignedBeaconBlock finalizedBlock = DataStructureUtil.randomSignedBeaconBlock(10, 1);
    final Checkpoint checkpoint = finalizedCheckpoint(finalizedBlock);
    eventBus.post(new FinalizedCheckpointEvent(checkpoint));
    final long slot = checkpoint.getEpochSlot().longValue() + 10;
    setSlot(slot);

    final long blockSlot = checkpoint.getEpochSlot().longValue();
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(blockSlot, 1);

    pendingPool.add(block);
    assertThat(pendingPool.contains(block)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(0);
    assertThat(pendingPool.getItemsDependingOn(block.getParent_root(), false)).isEmpty();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  private Checkpoint finalizedCheckpoint(SignedBeaconBlock block) {
    final UnsignedLong epoch = compute_epoch_at_slot(block.getSlot()).plus(UnsignedLong.ONE);
    final Bytes32 root = block.getMessage().hash_tree_root();

    return new Checkpoint(epoch, root);
  }

  @Test
  public void add_duplicateBlock() {
    final SignedBeaconBlock block =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    final Bytes32 parentRoot = block.getParent_root();
    pendingPool.add(block);
    pendingPool.add(block);

    assertThat(pendingPool.contains(block)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(block.getParent_root(), false))
        .containsExactlyInAnyOrder(block);
    assertThat(requiredRootEvents).containsExactly(parentRoot);
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void add_siblingBlocks() {
    final SignedBeaconBlock blockA =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    final Bytes32 parentRoot = blockA.getParent_root();
    final SignedBeaconBlock blockB =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), parentRoot, 3);
    pendingPool.add(blockA);
    pendingPool.add(blockB);

    assertThat(pendingPool.contains(blockA)).isTrue();
    assertThat(pendingPool.contains(blockB)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(2);
    assertThat(pendingPool.getItemsDependingOn(blockA.getParent_root(), false))
        .containsExactlyInAnyOrder(blockA, blockB);
    assertThat(requiredRootEvents).containsExactly(parentRoot);
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void remove_existingBlock() {
    final SignedBeaconBlock block =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    pendingPool.add(block);
    pendingPool.remove(block);

    assertThat(pendingPool.contains(block)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(0);
    assertThat(pendingPool.getItemsDependingOn(block.getParent_root(), false)).isEmpty();
    assertThat(requiredRootEvents).containsExactly(block.getParent_root());
    assertThat(requiredRootDroppedEvents).containsExactly(block.getParent_root());
  }

  @Test
  public void remove_unknownBlock() {
    final SignedBeaconBlock block =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    pendingPool.remove(block);

    assertThat(pendingPool.contains(block)).isFalse();
    assertThat(pendingPool.size()).isEqualTo(0);
    assertThat(pendingPool.getItemsDependingOn(block.getParent_root(), false)).isEmpty();
    assertThat(requiredRootEvents).isEmpty();
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void remove_siblingBlock() {
    final SignedBeaconBlock blockA =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    final Bytes32 parentRoot = blockA.getParent_root();
    final SignedBeaconBlock blockB =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), parentRoot, 3);
    pendingPool.add(blockA);
    pendingPool.add(blockB);
    pendingPool.remove(blockA);

    assertThat(pendingPool.contains(blockB)).isTrue();
    assertThat(pendingPool.size()).isEqualTo(1);
    assertThat(pendingPool.getItemsDependingOn(blockA.getParent_root(), false))
        .containsExactlyInAnyOrder(blockB);
    assertThat(requiredRootEvents).containsExactly(parentRoot);
    assertThat(requiredRootDroppedEvents).isEmpty();
  }

  @Test
  public void getItemsDependingOn_includeIndirect() {
    int seed = 9248294;
    final int chainDepth = 2;
    final int descendentChainCount = 2;
    final List<SignedBeaconBlock> directDescendents = new ArrayList<>();
    final List<SignedBeaconBlock> indirectDescendents = new ArrayList<>();

    final Bytes32 commonAncestorRoot = DataStructureUtil.randomBytes32(1);
    for (int chainIndex = 0; chainIndex < descendentChainCount; chainIndex++) {
      Bytes32 parentRoot = commonAncestorRoot;
      for (int depth = 0; depth < chainDepth; depth++) {
        final long slot = currentSlot.longValue() + 1 + depth;
        final SignedBeaconBlock block =
            DataStructureUtil.randomSignedBeaconBlock(slot, parentRoot, seed++);
        final List<SignedBeaconBlock> blockSet =
            depth == 0 ? directDescendents : indirectDescendents;
        blockSet.add(block);

        parentRoot = block.getMessage().hash_tree_root();
      }
    }

    indirectDescendents.forEach(pendingPool::add);
    directDescendents.forEach(pendingPool::add);

    List<SignedBeaconBlock> dependentBlocks =
        pendingPool.getItemsDependingOn(commonAncestorRoot, true);
    assertThat(dependentBlocks.size())
        .isEqualTo(directDescendents.size() + indirectDescendents.size());
    assertThat(dependentBlocks).containsAll(directDescendents);
    assertThat(dependentBlocks).containsAll(indirectDescendents);
  }

  @Test
  public void getItemsDependingOn_directOnly() {
    int seed = 48929482;
    final int chainDepth = 2;
    final int descendentChainCount = 2;
    final List<SignedBeaconBlock> directDescendents = new ArrayList<>();
    final List<SignedBeaconBlock> indirectDescendents = new ArrayList<>();

    final Bytes32 commonAncestorRoot = DataStructureUtil.randomBytes32(1);
    for (int chainIndex = 0; chainIndex < descendentChainCount; chainIndex++) {
      Bytes32 parentRoot = commonAncestorRoot;
      for (int depth = 0; depth < chainDepth; depth++) {
        final long slot = currentSlot.longValue() + 1 + depth;
        final SignedBeaconBlock block =
            DataStructureUtil.randomSignedBeaconBlock(slot, parentRoot, seed++);
        final List<SignedBeaconBlock> blockSet =
            depth == 0 ? directDescendents : indirectDescendents;
        blockSet.add(block);

        parentRoot = block.getMessage().hash_tree_root();
      }
    }

    indirectDescendents.forEach(pendingPool::add);
    directDescendents.forEach(pendingPool::add);

    List<SignedBeaconBlock> dependentBlocks =
        pendingPool.getItemsDependingOn(commonAncestorRoot, false);
    assertThat(dependentBlocks).containsExactlyInAnyOrderElementsOf(directDescendents);
  }

  @Test
  public void prune_finalizedBlocks() {
    final SignedBeaconBlock finalizedBlock = DataStructureUtil.randomSignedBeaconBlock(10, 1);
    final Checkpoint checkpoint = finalizedCheckpoint(finalizedBlock);
    final long finalizedSlot = checkpoint.getEpochSlot().longValue();
    setSlot(finalizedSlot);

    // Add a bunch of blocks
    List<SignedBeaconBlock> nonFinalBlocks =
        List.of(DataStructureUtil.randomSignedBeaconBlock(finalizedSlot + 1, 1));
    List<SignedBeaconBlock> finalizedBlocks =
        List.of(
            DataStructureUtil.randomSignedBeaconBlock(finalizedSlot, 2),
            DataStructureUtil.randomSignedBeaconBlock(finalizedSlot - 1, 3));
    List<SignedBeaconBlock> allBlocks = new ArrayList<>();
    allBlocks.addAll(nonFinalBlocks);
    allBlocks.addAll(finalizedBlocks);
    nonFinalBlocks.forEach(pendingPool::add);
    finalizedBlocks.forEach(pendingPool::add);

    // Check that all blocks are in the collection
    assertThat(pendingPool.size()).isEqualTo(finalizedBlocks.size() + nonFinalBlocks.size());
    for (SignedBeaconBlock block : allBlocks) {
      assertThat(pendingPool.contains(block)).isTrue();
    }

    // Update finalized checkpoint and prune
    eventBus.post(new FinalizedCheckpointEvent(checkpoint));
    pendingPool.prune();

    // Check that all final blocks have been pruned
    assertThat(pendingPool.size()).isEqualTo(nonFinalBlocks.size());
    for (SignedBeaconBlock block : nonFinalBlocks) {
      assertThat(pendingPool.contains(block)).isTrue();
    }
  }

  @Test
  public void onSlot_prunesOldBlocks() {
    final SignedBeaconBlock blockA =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue() - 1L, 1);
    final SignedBeaconBlock blockB =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    pendingPool.add(blockA);
    pendingPool.add(blockB);

    assertThat(pendingPool.contains(blockA)).isTrue();
    assertThat(pendingPool.contains(blockB)).isTrue();

    UnsignedLong newSlot = currentSlot;
    for (int i = 0; i < historicalTolerance.intValue() - 1; i++) {
      newSlot = newSlot.plus(UnsignedLong.ONE);
      pendingPool.onSlot(new SlotEvent(newSlot));
      assertThat(pendingPool.contains(blockA)).isTrue();
      assertThat(pendingPool.contains(blockB)).isTrue();
    }

    // Next slot should prune blockA
    pendingPool.onSlot(new SlotEvent(newSlot.plus(UnsignedLong.ONE)));

    assertThat(pendingPool.contains(blockA)).isFalse();
    assertThat(pendingPool.contains(blockB)).isTrue();
  }
}
