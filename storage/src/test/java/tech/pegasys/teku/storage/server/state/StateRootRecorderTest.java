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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.SpecProviderFactory;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class StateRootRecorderTest {
  private final SpecProvider specProvider = SpecProviderFactory.createMinimal();
  DataStructureUtil dataStructureUtil = new DataStructureUtil(specProvider);
  final Map<UInt64, Bytes32> stateRoots = new HashMap<>();
  final BeaconState state = dataStructureUtil.randomBeaconState();
  final UInt64 slot = state.getSlot();

  @Test
  public void shouldHandleSingleStep() {
    final StateRootRecorder stateRootRecorder =
        new StateRootRecorder(
            slot, (stateRoot, slot) -> stateRoots.put(slot, stateRoot), specProvider);
    stateRootRecorder.acceptNextState(state);
    assertThat(stateRoots).containsOnlyKeys(slot);
  }

  @Test
  public void shouldHandleMultipleSteps() {
    final StateRootRecorder stateRootRecorder =
        new StateRootRecorder(
            slot.minus(2), (stateRoot, slot) -> stateRoots.put(slot, stateRoot), specProvider);
    stateRootRecorder.acceptNextState(state);
    assertThat(stateRoots).containsOnlyKeys(slot.minus(2), slot.minus(ONE), slot);
  }

  @Test
  public void shouldLimitToSlotsPerHistoricalRoot() {
    final UInt64 history =
        UInt64.valueOf(specProvider.getGenesisSpecConstants().getSlotsPerHistoricalRoot()).plus(10);
    final StateRootRecorder stateRootRecorder =
        new StateRootRecorder(
            slot.minus(history),
            (stateRoot, slot) -> stateRoots.put(slot, stateRoot),
            specProvider);
    stateRootRecorder.acceptNextState(state);
    assertThat(stateRoots.size())
        .isEqualTo(specProvider.getGenesisSpecConstants().getSlotsPerHistoricalRoot() + 1);
  }
}
