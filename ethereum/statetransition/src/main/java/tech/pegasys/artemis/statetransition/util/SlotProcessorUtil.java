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

package tech.pegasys.artemis.statetransition.util;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.BeaconState;

import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;

public final class SlotProcessorUtil {

  public static void process_slot(BeaconState state) {
    // Cache state root
    Bytes32 previous_state_root = state.hash_tree_root();
    int index = state.getSlot().mod(UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT)).intValue();
    state.getLatest_state_roots().set(index, previous_state_root);

    // Cache latest block header state root
    if (state.getLatest_block_header().getState_root().equals(ZERO_HASH)) {
      state.getLatest_block_header().setState_root(previous_state_root);
    }

    // Cache block root
    Bytes32 previous_block_root = state.getLatest_block_header().signing_root("signature");
    state.getLatest_block_roots().set(index, previous_block_root);
  }

  public static void process

}
