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

/**
 * A pure state transition interface.
 *
 * <p>Used as an interface to per-slot and per-epoch transitions that accepts only a source state.
 *
 * @param <State> a state type.
 * @see BlockTransition
 */
public interface StateTransition<State> {

  /**
   * Applies a transition function.
   *
   * @param source a source state.
   * @return a source state modified by a transition function.
   */
  State apply(State source);
}
