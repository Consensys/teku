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

import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;

import com.google.common.primitives.UnsignedLong;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.BeaconState;

public class StateRootRecorder {
  private static final Logger LOG = LogManager.getLogger();
  private final UnsignedLong slotsPerHistoricalRoot;
  private UnsignedLong slot;
  private final BiConsumer<Bytes32, UnsignedLong> stateRootConsumer;

  public StateRootRecorder(
      final UnsignedLong slot, final BiConsumer<Bytes32, UnsignedLong> stateRootConsumer) {
    this.stateRootConsumer = stateRootConsumer;
    this.slot = slot;
    this.slotsPerHistoricalRoot = UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT);
  }

  public void acceptNextState(final BeaconState state) {
    if (slot.plus(slotsPerHistoricalRoot).compareTo(state.getSlot()) < 0) {
      final UnsignedLong floor = state.getSlot().minus(slotsPerHistoricalRoot);
      LOG.warn("Missing state root mappings from slot {} to {}", slot, floor);
      slot = floor;
    }

    while (slot.compareTo(state.getSlot()) < 0) {
      stateRootConsumer.accept(
          state.getState_roots().get(slot.mod(slotsPerHistoricalRoot).intValue()), slot);
      slot = slot.plus(UnsignedLong.ONE);
    }

    stateRootConsumer.accept(state.hash_tree_root(), state.getSlot());
  }
}
