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

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.events.SlotEvent;

public class PendingBlocksTest {
  private final EventBus eventBus = new EventBus();
  private final UnsignedLong historicalTolerance = UnsignedLong.valueOf(5);
  private final UnsignedLong futureTolerance = UnsignedLong.valueOf(2);
  private final PendingBlocks pendingBlocks =
      new PendingBlocks(eventBus, historicalTolerance, futureTolerance);
  private final UnsignedLong currentSlot = historicalTolerance.times(UnsignedLong.valueOf(2));

  @BeforeEach
  public void setup() {
    // Set up slot
    assertThat(pendingBlocks.start()).isCompleted();
    eventBus.post(new SlotEvent(currentSlot));
  }

  @AfterEach
  public void cleanup() {
    assertThat(pendingBlocks.stop()).isCompleted();
  }

  @Test
  public void add_blockForCurrentSlot() {
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(currentSlot.longValue(), 1);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(block.getParent_root())).containsExactlyInAnyOrder(block);
  }

  @Test
  public void add_historicalBlockWithinWindow() {
    final UnsignedLong slot = currentSlot.minus(historicalTolerance);
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(slot.longValue(), 1);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(block.getParent_root())).containsExactlyInAnyOrder(block);
  }

  @Test
  public void add_historicalBlockOutsideWindow() {
    final UnsignedLong slot = currentSlot.minus(historicalTolerance).minus(UnsignedLong.ONE);
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(slot.longValue(), 1);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isFalse();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.childrenOf(block.getParent_root())).isEmpty();
  }

  @Test
  public void add_futureBlockWithinWindow() {
    final UnsignedLong slot = currentSlot.plus(futureTolerance);
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(slot.longValue(), 1);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(block.getParent_root())).containsExactlyInAnyOrder(block);
  }

  @Test
  public void add_futureBlockOutsideWindow() {
    final UnsignedLong slot = currentSlot.plus(futureTolerance).plus(UnsignedLong.ONE);
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(slot.longValue(), 1);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isFalse();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.childrenOf(block.getParent_root())).isEmpty();
  }

  @Test
  public void add_duplicateBlock() {
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(currentSlot.longValue(), 1);
    pendingBlocks.add(block);
    pendingBlocks.add(block);

    assertThat(pendingBlocks.contains(block)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(block.getParent_root())).containsExactlyInAnyOrder(block);
  }

  @Test
  public void add_siblingBlocks() {
    final BeaconBlock blockA = DataStructureUtil.randomBeaconBlock(currentSlot.longValue(), 1);
    final BeaconBlock blockB =
        DataStructureUtil.randomBeaconBlock(currentSlot.longValue(), blockA.getParent_root(), 2);
    pendingBlocks.add(blockA);
    pendingBlocks.add(blockB);

    assertThat(pendingBlocks.contains(blockA)).isTrue();
    assertThat(pendingBlocks.contains(blockB)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(2);
    assertThat(pendingBlocks.childrenOf(blockA.getParent_root()))
        .containsExactlyInAnyOrder(blockA, blockB);
  }

  @Test
  public void remove_existingBlock() {
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(currentSlot.longValue(), 1);
    pendingBlocks.add(block);
    pendingBlocks.remove(block);

    assertThat(pendingBlocks.contains(block)).isFalse();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.childrenOf(block.getParent_root())).isEmpty();
  }

  @Test
  public void remove_unknownBlock() {
    final BeaconBlock block = DataStructureUtil.randomBeaconBlock(currentSlot.longValue(), 1);
    pendingBlocks.remove(block);

    assertThat(pendingBlocks.contains(block)).isFalse();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.childrenOf(block.getParent_root())).isEmpty();
  }

  @Test
  public void remove_siblingBlock() {
    final BeaconBlock blockA = DataStructureUtil.randomBeaconBlock(currentSlot.longValue(), 1);
    final BeaconBlock blockB =
        DataStructureUtil.randomBeaconBlock(currentSlot.longValue(), blockA.getParent_root(), 2);
    pendingBlocks.add(blockA);
    pendingBlocks.add(blockB);
    pendingBlocks.remove(blockA);

    assertThat(pendingBlocks.contains(blockB)).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(pendingBlocks.childrenOf(blockA.getParent_root())).containsExactlyInAnyOrder(blockB);
  }

  @Test
  public void onSlot_prunesOldBlocks() {
    final BeaconBlock blockA = DataStructureUtil.randomBeaconBlock(currentSlot.longValue() - 1L, 1);
    final BeaconBlock blockB = DataStructureUtil.randomBeaconBlock(currentSlot.longValue(), 1);
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
