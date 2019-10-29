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

package org.ethereum.beacon.consensus;

import org.ethereum.beacon.core.BeaconBlock;

/**
 * A state transition interface accepting a {@link BeaconBlock} as input data.
 *
 * <p>Used as an interface to per-block transition which applies an information from given block to
 * a source state.
 *
 * @param <State> a state type.
 * @see StateTransition
 */
public interface BlockTransition<State> {

  /**
   * Applies a state transition function to given source using given block as an input.
   *
   * @param source a source state.
   * @param input a beacon block with input data.
   * @return a source state modified by a transition function with a help of block data.
   */
  State apply(State source, BeaconBlock input);
}
