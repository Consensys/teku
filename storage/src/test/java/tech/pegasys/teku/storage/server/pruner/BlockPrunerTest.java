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

package tech.pegasys.teku.storage.server.pruner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.Database;

class BlockPrunerTest {

  public static final Duration PRUNE_INTERVAL = Duration.ofSeconds(26);
  public static final int EPOCHS_TO_KEEP = 5;
  public static final int SLOTS_PER_EPOCH = 10;
  // Nice simple number of slots per epoch
  private final Spec spec =
      TestSpecFactory.createDefault(builder -> builder.slotsPerEpoch(SLOTS_PER_EPOCH));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final Database database = mock(Database.class);

  private final BlockPruner pruner =
      new BlockPruner(spec, database, asyncRunner, PRUNE_INTERVAL, EPOCHS_TO_KEEP);

  @BeforeEach
  void setUp() {
    assertThat(pruner.start()).isCompleted();
  }

  @Test
  void shouldNotPruneWhenFinalizedCheckpointNotSet() {
    when(database.getFinalizedCheckpoint()).thenReturn(Optional.empty());
    triggerNextPruning();
    verify(database, never()).pruneFinalizedBlocks(any(), any());
  }

  @Test
  void shouldNotPruneWhenFinalizedCheckpointBelowEpochsToKeep() {
    when(database.getFinalizedCheckpoint())
        .thenReturn(Optional.of(dataStructureUtil.randomCheckpoint(EPOCHS_TO_KEEP)));
    triggerNextPruning();
    verify(database, never()).pruneFinalizedBlocks(any(), any());
  }

  @Test
  void shouldPruneBlocksMoreThanEpochsToKeepBeforeFinalizedCheckpoint() {
    final UInt64 finalizedEpoch = UInt64.valueOf(50);
    when(database.getFinalizedCheckpoint())
        .thenReturn(Optional.of(dataStructureUtil.randomCheckpoint(finalizedEpoch)));
    triggerNextPruning();
    // SlotToKeep = FinalizedEpoch (50) * SlotsPerEpoch(10) - EpochsToKeep(5) * SlotsPerEpoch(10)
    // = 500 - 50 = 450, last slot to prune = 550 - 1 = 449.
    final UInt64 lastSlotToPrune = UInt64.valueOf(449);
    verify(database).pruneFinalizedBlocks(UInt64.ZERO, lastSlotToPrune);
  }

  @Test
  void shouldPruneBlocksWhenFirstEpochIsPrunable() {
    final int finalizedEpoch = EPOCHS_TO_KEEP + 1;
    when(database.getFinalizedCheckpoint())
        .thenReturn(Optional.of(dataStructureUtil.randomCheckpoint(finalizedEpoch)));
    triggerNextPruning();
    // Should prune all blocks in the first epoch (ie blocks 0 - 9)
    final UInt64 lastSlotToPrune = UInt64.valueOf(SLOTS_PER_EPOCH - 1);
    verify(database).pruneFinalizedBlocks(UInt64.ZERO, lastSlotToPrune);
  }

  private void triggerNextPruning() {
    timeProvider.advanceTimeBy(PRUNE_INTERVAL);
    asyncRunner.executeDueActions();
  }
}
