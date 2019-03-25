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

import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_BLOCK_ROOTS_LENGTH;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;

public final class SlotProcessorUtil {

  public static void updateBlockRoots(BeaconState state, Bytes32 previous_block_root)
      throws SlotProcessingException {

    if (state.getSlot().compareTo(UnsignedLong.valueOf(Constants.GENESIS_SLOT)) > 0) {
      List<Bytes32> latest_block_roots = state.getLatest_block_roots();

      latest_block_roots.set(
          toIntExact(state.getSlot().intValue() - 1) % Constants.LATEST_BLOCK_ROOTS_LENGTH,
          previous_block_root);
      state.setLatest_block_roots(latest_block_roots);
    }

    if (state
            .getSlot()
            .mod(UnsignedLong.valueOf(LATEST_BLOCK_ROOTS_LENGTH))
            .compareTo(UnsignedLong.ZERO)
        == 0) {
      List<Bytes32> batched_block_roots = state.getBatched_block_roots();
      List<Bytes32> latest_block_roots = state.getLatest_block_roots();
      if (batched_block_roots != null && latest_block_roots != null) {
        Bytes32 merkle_root = BeaconStateUtil.merkle_root(latest_block_roots);
        batched_block_roots.add(merkle_root);
      } else
        throw new SlotProcessingException(
            "SlotProcessingException: BeaconState cannot be updated due to "
                + "batched_block_roots and latest_block_roots returning a null");
    }
  }
}
