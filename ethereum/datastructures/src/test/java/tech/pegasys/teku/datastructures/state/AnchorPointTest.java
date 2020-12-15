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

package tech.pegasys.teku.datastructures.state;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AnchorPointTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  public void create_withCheckpointPriorToState() {
    final UInt64 epoch = UInt64.valueOf(10);
    final UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);

    final BeaconBlockAndState blockAndState =
        dataStructureUtil.randomBlockAndState(epochStartSlot.plus(1));
    final Checkpoint checkpoint = new Checkpoint(epoch, blockAndState.getRoot());

    assertThatThrownBy(
            () -> AnchorPoint.create(checkpoint, blockAndState.getState(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Block must be at or prior to the start of the checkpoint epoch");
  }
}
