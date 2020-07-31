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

import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.core.blockvalidator.NoOpBlockValidator;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;

class BlockProcessor {
  private final StateTransition stateTransition = new StateTransition(new NoOpBlockValidator());

  public BeaconState process(final BeaconState preState, final SignedBeaconBlock block) {

    try {
      final BeaconState postState = stateTransition.initiate(preState, block);
      assertBlockAndStateMatch(block, postState);
      return postState;
    } catch (StateTransitionException e) {
      throw new IllegalStateException(getFailedStateGenerationError(block), e);
    }
  }

  public void assertBlockAndStateMatch(final SignedBeaconBlock block, final BeaconState state) {
    if (!block.getStateRoot().equals(state.hash_tree_root())) {
      final String msg =
          String.format(
              "Failed to regenerate state for block root %s.  Generated state root %s does not match expected state root %s",
              block.getRoot(), state.hash_tree_root(), block.getStateRoot());
      throw new IllegalStateException(msg);
    }
  }

  private String getFailedStateGenerationError(final SignedBeaconBlock block) {
    return String.format(
        "Unable to produce state for block at slot %s (%s)", block.getSlot(), block.getRoot());
  }
}
