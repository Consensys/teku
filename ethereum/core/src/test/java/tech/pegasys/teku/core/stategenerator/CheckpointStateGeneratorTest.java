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

package tech.pegasys.teku.core.stategenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class CheckpointStateGeneratorTest {
  private final ChainBuilder chainBuilder = ChainBuilder.createDefault();

  @BeforeEach
  public void setup() {
    chainBuilder.generateGenesis();
  }

  @Test
  public void generate_withBlockPriorToEpoch() {
    final SignedBlockAndState checkpointBlockAndState = chainBuilder.generateNextBlock();

    final Checkpoint checkpoint = chainBuilder.getCurrentCheckpointForEpoch(5);
    final CheckpointState result =
        CheckpointStateGenerator.generate(checkpoint, checkpointBlockAndState);

    assertThat(result.getBlock()).isEqualTo(checkpointBlockAndState.getBlock());
    assertThat(result.getState()).isNotEqualTo(checkpointBlockAndState.getState());
    assertThat(result.getState().getSlot()).isEqualTo(checkpoint.getEpochStartSlot());
  }

  @Test
  public void generate_withBlockAtEpochBoundary() {
    final UInt64 epoch = UInt64.valueOf(2);
    final UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);

    chainBuilder.generateBlocksUpToSlot(epochStartSlot);

    final Checkpoint checkpoint = chainBuilder.getCurrentCheckpointForEpoch(epoch);
    final SignedBlockAndState checkpointBlockAndState = chainBuilder.getLatestBlockAndState();
    final CheckpointState result =
        CheckpointStateGenerator.generate(checkpoint, checkpointBlockAndState);

    assertThat(result.getBlock()).isEqualTo(checkpointBlockAndState.getBlock());
    assertThat(result.getState()).isEqualTo(checkpointBlockAndState.getState());
    assertThat(result.getState().getSlot()).isEqualTo(checkpoint.getEpochStartSlot());
  }

  @Test
  public void generate_withInvalidBlockPastTheEpochBoundary() {
    final UInt64 epoch = UInt64.valueOf(2);
    final UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);

    chainBuilder.generateBlocksUpToSlot(epochStartSlot);

    final SignedBlockAndState checkpointBlockAndState = chainBuilder.generateNextBlock();
    final Checkpoint checkpoint = new Checkpoint(epoch, checkpointBlockAndState.getRoot());
    assertThatThrownBy(() -> CheckpointStateGenerator.generate(checkpoint, checkpointBlockAndState))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
