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

package tech.pegasys.teku.api.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class BeaconChainHeadTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final BeaconBlockAndState blockAndState = dataStructureUtil.randomBlockAndState(1);

  @Test
  public void shouldCreateFromBlockAndState() {
    final BeaconChainHead beaconChainHead = new BeaconChainHead(blockAndState);
    final UInt64 latestSlot = blockAndState.getSlot();
    final UInt64 headEpoch = compute_epoch_at_slot(latestSlot);

    assertThat(beaconChainHead.head_block_root).isEqualTo(blockAndState.getRoot());
    assertThat(beaconChainHead.head_epoch).isEqualTo(headEpoch);
    assertThat(beaconChainHead.head_slot).isEqualTo(blockAndState.getSlot());

    final Checkpoint finalized = blockAndState.getState().getFinalized_checkpoint();
    assertThat(beaconChainHead.finalized_slot).isEqualTo(finalized.getEpochStartSlot());
    assertThat(beaconChainHead.finalized_epoch).isEqualTo(finalized.getEpoch());
    assertThat(beaconChainHead.finalized_block_root).isEqualTo(finalized.getRoot());

    final Checkpoint justified = blockAndState.getState().getCurrent_justified_checkpoint();
    assertThat(beaconChainHead.justified_slot).isEqualTo(justified.getEpochStartSlot());
    assertThat(beaconChainHead.justified_epoch).isEqualTo(justified.getEpoch());
    assertThat(beaconChainHead.justified_block_root).isEqualTo(justified.getRoot());

    final Checkpoint previous = blockAndState.getState().getPrevious_justified_checkpoint();
    assertThat(beaconChainHead.previous_justified_slot).isEqualTo(previous.getEpochStartSlot());
    assertThat(beaconChainHead.previous_justified_epoch).isEqualTo(previous.getEpoch());
    assertThat(beaconChainHead.previous_justified_block_root).isEqualTo(previous.getRoot());
  }
}
