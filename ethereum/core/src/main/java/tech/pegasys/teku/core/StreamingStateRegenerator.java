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

package tech.pegasys.teku.core;

import java.util.stream.Stream;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;

/**
 * This class is only suitable for regenerating states we have previously performed full validation
 * on. It assumes that the pre state and supplied blocks are valid and does not recheck state roots
 * or signatures.
 */
public class StreamingStateRegenerator {

  private final StateTransition stateTransition = new StateTransition();
  private BeaconState state;

  private StreamingStateRegenerator(final BeaconState preState) {
    this.state = preState;
  }

  private void processBlock(final SignedBeaconBlock block) {
    try {
      state = stateTransition.initiate(state, block, false);
    } catch (StateTransitionException e) {
      throw new IllegalStateException("Regenerating state failed", e);
    }
  }

  public static BeaconState regenerate(
      final BeaconState initialState, final Stream<SignedBeaconBlock> blocks) {
    final StreamingStateRegenerator regenerator = new StreamingStateRegenerator(initialState);
    blocks.forEach(regenerator::processBlock);
    return regenerator.state;
  }
}
