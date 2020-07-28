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

package tech.pegasys.teku.storage.server.state;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

public class StateRootRecorderTest {
  DataStructureUtil dataStructureUtil = new DataStructureUtil();
  final Map<UnsignedLong, Bytes32> stateRoots = new HashMap<>();
  final BeaconState state = dataStructureUtil.randomBeaconState();
  final UnsignedLong slot = state.getSlot();

  @Test
  public void shouldHandleSingleStep() {
    final StateRootRecorder stateRootRecorder =
        new StateRootRecorder(
            slot.minus(ONE), (stateRoot, slot) -> stateRoots.put(slot, stateRoot));
    stateRootRecorder.acceptNextState(state);
    assertThat(stateRoots).containsOnlyKeys(slot.minus(ONE));
  }

  @Test
  public void shouldHandleMultipleSteps() {
    final StateRootRecorder stateRootRecorder =
        new StateRootRecorder(
            slot.minus(UnsignedLong.valueOf(2)),
            (stateRoot, slot) -> stateRoots.put(slot, stateRoot));
    stateRootRecorder.acceptNextState(state);
    assertThat(stateRoots).containsOnlyKeys(slot.minus(UnsignedLong.valueOf(2)), slot.minus(ONE));
  }

  @Test
  public void shouldHandleNoStep() {
    final StateRootRecorder stateRootRecorder =
        new StateRootRecorder(slot, (stateRoot, slot) -> stateRoots.put(slot, stateRoot));
    stateRootRecorder.acceptNextState(state);
    assertThat(stateRoots).isEmpty();
  }

  @Test
  public void shouldLimitToSlotsPerHistoricalRoot() {
    final UnsignedLong history =
        UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT).plus(UnsignedLong.valueOf(10));
    final StateRootRecorder stateRootRecorder =
        new StateRootRecorder(
            slot.minus(history), (stateRoot, slot) -> stateRoots.put(slot, stateRoot));
    stateRootRecorder.acceptNextState(state);
    assertThat(stateRoots.size()).isEqualTo(SLOTS_PER_HISTORICAL_ROOT);
  }
}
