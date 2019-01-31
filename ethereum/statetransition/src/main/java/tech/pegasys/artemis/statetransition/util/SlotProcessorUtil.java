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
import static tech.pegasys.artemis.datastructures.Constants.LATEST_RANDAO_MIXES_LENGTH;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.ValidatorRecord;
import tech.pegasys.artemis.datastructures.state.Validators;
import tech.pegasys.artemis.statetransition.BeaconState;
import tech.pegasys.artemis.statetransition.StateTransitionException;

public class SlotProcessorUtil {

  public static void updateProposerRandaoLayer(BeaconState state) {
    int currSlot = toIntExact(state.getSlot());
    int proposerIndex = BeaconState.get_beacon_proposer_index(state, currSlot);

    Validators validators = state.getValidator_registry();
    ValidatorRecord proposerRecord = validators.get(proposerIndex);
    proposerRecord.setRandao_layers(proposerRecord.getRandao_layers().plus(UnsignedLong.ONE));
  }

  public static void updateLatestRandaoMixes(BeaconState state) {
    int currSlot = toIntExact(state.getSlot());
    ArrayList<Bytes32> latestRandaoMixes = state.getLatest_randao_mixes();
    Bytes32 prevSlotRandaoMix = latestRandaoMixes.get((currSlot - 1) % LATEST_RANDAO_MIXES_LENGTH);
    latestRandaoMixes.set(currSlot % LATEST_RANDAO_MIXES_LENGTH, prevSlotRandaoMix);
  }

  public static void updateRecentBlockHashes(BeaconState state, BeaconBlock block)
      throws Exception {
    Bytes32 previous_block_root = BeaconStateUtil.get_block_root(state, state.getSlot() - 1);
    if (previous_block_root != null) {
      ArrayList<Bytes32> latest_block_roots = state.getLatest_block_roots();

      latest_block_roots.set(
          toIntExact(state.getSlot() - 1) % Constants.LATEST_BLOCK_ROOTS_LENGTH,
          previous_block_root);
      state.setLatest_block_roots(latest_block_roots);
    } else {
      throw new StateTransitionException(
          "StateTransitionException: BeaconState cannot be updated due to "
              + "previous_block_root returning a null");
    }

    if (state.getSlot() % LATEST_BLOCK_ROOTS_LENGTH == 0) {
      ArrayList<Bytes32> batched_block_roots = state.getBatched_block_roots();
      ArrayList<Bytes32> latest_block_roots = state.getLatest_block_roots();
      if (batched_block_roots != null && latest_block_roots != null) {
        Bytes32 merkle_root = BeaconStateUtil.merkle_root(latest_block_roots);
        batched_block_roots.add(merkle_root);
      } else
        throw new StateTransitionException(
            "StateTransitionException: BeaconState cannot be updated due to "
                + "batched_block_roots and latest_block_roots returning a null");
    }
  }
}
