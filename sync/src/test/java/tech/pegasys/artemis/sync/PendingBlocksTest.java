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
import tech.pegasys.artemis.util.time.SlotEvent;

public class PendingBlocksTest {
  private final EventBus eventBus = new EventBus();
  private final UnsignedLong historicalTolerance = UnsignedLong.valueOf(5);
  private final UnsignedLong futureTolerance = UnsignedLong.valueOf(2);
  private final PendingBlocks pendingBlocks =
      new PendingBlocks(eventBus, historicalTolerance, futureTolerance);
  private UnsignedLong currentSlot = historicalTolerance.times(UnsignedLong.valueOf(2));

  @BeforeEach
  public void setup() {
    // Set up slot
    assertThat(pendingBlocks.start()).isCompleted();
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
    assertThat(pendingBlocks.stop()).isCompleted();
  }

  @Test
  public void add_blockForCurrentSlot() {
    final SignedBeaconBlock block =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(block.getMessage().getParent_root()))
        .containsExactlyInAnyOrder(block);
  }

  @Test
  public void add_historicalBlockWithinWindow() {
    final UnsignedLong slot = currentSlot.minus(historicalTolerance);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), 1);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(block.getMessage().getParent_root()))
        .containsExactlyInAnyOrder(block);
  }

  @Test
  public void add_historicalBlockOutsideWindow() {
    final UnsignedLong slot = currentSlot.minus(historicalTolerance).minus(UnsignedLong.ONE);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), 1);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isFalse();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.childrenOf(block.getMessage().getParent_root())).isEmpty();
  }

  @Test
  public void add_futureBlockWithinWindow() {
    final UnsignedLong slot = currentSlot.plus(futureTolerance);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), 1);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(block.getMessage().getParent_root()))
        .containsExactlyInAnyOrder(block);
  }

  @Test
  public void add_futureBlockOutsideWindow() {
    final UnsignedLong slot = currentSlot.plus(futureTolerance).plus(UnsignedLong.ONE);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), 1);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isFalse();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.childrenOf(block.getMessage().getParent_root())).isEmpty();
  }

  @Test
  public void add_nonFinalizedBlock() {
    final SignedBeaconBlock finalizedBlock = DataStructureUtil.randomSignedBeaconBlock(10, 1);
    final Checkpoint checkpoint = finalizedCheckpoint(finalizedBlock);
    eventBus.post(new FinalizedCheckpointEvent(checkpoint));

    final UnsignedLong slot = checkpoint.getEpochSlot().plus(UnsignedLong.ONE);
    setSlot(slot);
    final SignedBeaconBlock block = DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), 1);

    pendingBlocks.add(block);
    assertThat(pendingBlocks.contains(block)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(block.getMessage().getParent_root()))
        .containsExactlyInAnyOrder(block);
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

    pendingBlocks.add(block);
    assertThat(pendingBlocks.contains(block)).isFalse();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.childrenOf(block.getMessage().getParent_root())).isEmpty();
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
    pendingBlocks.add(block);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(block.getMessage().getParent_root()))
        .containsExactlyInAnyOrder(block);
  }

  @Test
  public void add_siblingBlocks() {
    final SignedBeaconBlock blockA =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    final SignedBeaconBlock blockB =
        DataStructureUtil.randomSignedBeaconBlock(
            currentSlot.longValue(), blockA.getMessage().getParent_root(), 2);
    pendingBlocks.add(blockA);
    pendingBlocks.add(blockB);

    assertThat(pendingBlocks.contains(blockA)).isTrue();
    assertThat(pendingBlocks.contains(blockB)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(2);
    assertThat(pendingBlocks.childrenOf(blockA.getMessage().getParent_root()))
        .containsExactlyInAnyOrder(blockA, blockB);
  }

  @Test
  public void remove_existingBlock() {
    final SignedBeaconBlock block =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    pendingBlocks.add(block);
    pendingBlocks.remove(block);

    assertThat(pendingBlocks.contains(block)).isFalse();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.childrenOf(block.getMessage().getParent_root())).isEmpty();
  }

  @Test
  public void remove_unknownBlock() {
    final SignedBeaconBlock block =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    pendingBlocks.remove(block);

    assertThat(pendingBlocks.contains(block)).isFalse();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.childrenOf(block.getParent_root())).isEmpty();
  }

  @Test
  public void remove_siblingBlock() {
    final SignedBeaconBlock blockA =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    final SignedBeaconBlock blockB =
        DataStructureUtil.randomSignedBeaconBlock(
            currentSlot.longValue(), blockA.getParent_root(), 2);
    pendingBlocks.add(blockA);
    pendingBlocks.add(blockB);
    pendingBlocks.remove(blockA);

    assertThat(pendingBlocks.contains(blockB)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(blockA.getParent_root())).containsExactlyInAnyOrder(blockB);
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
    nonFinalBlocks.forEach(pendingBlocks::add);
    finalizedBlocks.forEach(pendingBlocks::add);

    // Check that all blocks are in the collection
    assertThat(pendingBlocks.size()).isEqualTo(finalizedBlocks.size() + nonFinalBlocks.size());
    for (SignedBeaconBlock block : allBlocks) {
      assertThat(pendingBlocks.contains(block)).isTrue();
    }

    // Update finalized checkpoint and prune
    eventBus.post(new FinalizedCheckpointEvent(checkpoint));
    pendingBlocks.prune();

    // Check that all final blocks have been pruned
    assertThat(pendingBlocks.size()).isEqualTo(nonFinalBlocks.size());
    for (SignedBeaconBlock block : nonFinalBlocks) {
      assertThat(pendingBlocks.contains(block)).isTrue();
    }
  }

  @Test
  public void onSlot_prunesOldBlocks() {
    final SignedBeaconBlock blockA =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue() - 1L, 1);
    final SignedBeaconBlock blockB =
        DataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue(), 1);
    pendingBlocks.add(blockA);
    pendingBlocks.add(blockB);

    assertThat(pendingBlocks.contains(blockA)).isTrue();
    assertThat(pendingBlocks.contains(blockB)).isTrue();

    UnsignedLong newSlot = currentSlot;
    for (int i = 0; i < historicalTolerance.intValue() - 1; i++) {
      newSlot = newSlot.plus(UnsignedLong.ONE);
      pendingBlocks.onSlot(new SlotEvent(newSlot));
      assertThat(pendingBlocks.contains(blockA)).isTrue();
      assertThat(pendingBlocks.contains(blockB)).isTrue();
    }

    // Next slot should prune blockA
    pendingBlocks.onSlot(new SlotEvent(newSlot.plus(UnsignedLong.ONE)));

    assertThat(pendingBlocks.contains(blockA)).isFalse();
    assertThat(pendingBlocks.contains(blockB)).isTrue();
  }
}
