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

package tech.pegasys.artemis.api.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

class BeaconChainHeadTest {
  private BeaconState beaconState = mock(BeaconState.class);
  private static final UnsignedLong latestSlot =
      compute_start_slot_at_epoch(UnsignedLong.valueOf(100000L));
  private static final UnsignedLong headEpoch = compute_epoch_at_slot(latestSlot);
  private static final Bytes32 headBlockRoot = Bytes32.random();
  private static final Bytes32 headParentRoot = Bytes32.random();
  private static final Bytes32 headStateRoot = Bytes32.random();

  private static final UnsignedLong justifiedSlot =
      compute_start_slot_at_epoch(UnsignedLong.valueOf(10000L));
  private static final UnsignedLong justifiedEpoch = compute_epoch_at_slot(justifiedSlot);
  private static final Bytes32 justifiedBlockRoot = Bytes32.random();
  private static final Checkpoint justifiedCheckpoint =
      new Checkpoint(justifiedEpoch, justifiedBlockRoot);

  private static final UnsignedLong previousSlot =
      compute_start_slot_at_epoch(UnsignedLong.valueOf(1000L));
  private static final UnsignedLong previousEpoch = compute_epoch_at_slot(previousSlot);
  private static final Bytes32 previousBlockRoot = Bytes32.random();
  private static final Checkpoint previousCheckpoint =
      new Checkpoint(previousEpoch, previousBlockRoot);

  private static final UnsignedLong finalizedSlot =
      compute_start_slot_at_epoch(UnsignedLong.valueOf(100L));
  private static final UnsignedLong finalizedEpoch = compute_epoch_at_slot(finalizedSlot);
  private static final Bytes32 finalizedBlockRoot = Bytes32.random();
  private static final Checkpoint finalizedCheckpoint =
      new Checkpoint(finalizedEpoch, finalizedBlockRoot);

  @Test
  public void shouldHaveDifferentValuesFromState() {
    tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader latestHeader =
        new tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader(
            latestSlot, headParentRoot, headStateRoot, headBlockRoot);
    when(beaconState.getLatest_block_header()).thenReturn(latestHeader);
    when(beaconState.getCurrent_justified_checkpoint()).thenReturn(justifiedCheckpoint);
    when(beaconState.getPrevious_justified_checkpoint()).thenReturn(previousCheckpoint);
    when(beaconState.getFinalized_checkpoint()).thenReturn(finalizedCheckpoint);

    final BeaconChainHead beaconChainHead = new BeaconChainHead(beaconState);

    verify(beaconState).getLatest_block_header();
    verify(beaconState).getCurrent_justified_checkpoint();
    verify(beaconState).getPrevious_justified_checkpoint();
    verify(beaconState).getFinalized_checkpoint();

    assertThat(beaconChainHead.head_block_root).isEqualTo(headBlockRoot);
    assertThat(beaconChainHead.head_epoch).isEqualTo(headEpoch);
    assertThat(beaconChainHead.head_slot).isEqualTo(latestSlot);

    assertThat(beaconChainHead.justified_slot).isEqualTo(justifiedSlot);
    assertThat(beaconChainHead.justified_epoch).isEqualTo(justifiedEpoch);
    assertThat(beaconChainHead.justified_block_root).isEqualTo(justifiedBlockRoot);

    assertThat(beaconChainHead.previous_justified_slot).isEqualTo(previousSlot);
    assertThat(beaconChainHead.previous_justified_epoch).isEqualTo(previousEpoch);
    assertThat(beaconChainHead.previous_justified_block_root).isEqualTo(previousBlockRoot);

    assertThat(beaconChainHead.finalized_slot).isEqualTo(finalizedSlot);
    assertThat(beaconChainHead.finalized_epoch).isEqualTo(finalizedEpoch);
    assertThat(beaconChainHead.finalized_block_root).isEqualTo(finalizedBlockRoot);
  }
}
