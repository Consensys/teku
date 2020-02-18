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

package tech.pegasys.artemis.datastructures.state;

public final class BeaconStateWithCache extends BeaconState {

  private final TransitionCaches transitionCaches;

  private BeaconStateWithCache(BeaconStateWithCache state) {
    super(state);
    transitionCaches = state.transitionCaches;
  }

  private BeaconStateWithCache(BeaconStateRead state) {
    super(state);
    transitionCaches = TransitionCaches.createNewEmpty();
  }

  public static BeaconStateWithCache deepCopy(BeaconStateRead state) {
    if (state instanceof BeaconStateWithCache) {
      return new BeaconStateWithCache((BeaconStateWithCache) state);
    } else {
      return new BeaconStateWithCache(state);
    }
  }

  /**
   * Creates a BeaconStateWithCache with empty caches from the given BeaconState.
   *
   * @param state state to create from
   * @return created state with empty caches
   */
  public static BeaconStateWithCache fromBeaconState(BeaconStateRead state) {
    if (state instanceof BeaconStateWithCache) {
      return (BeaconStateWithCache) state;
    } else {
      return new BeaconStateWithCache(state);
    }
  }

  public static TransitionCaches getTransitionCaches(BeaconStateRead state) {
    return state instanceof BeaconStateWithCache
        ? ((BeaconStateWithCache) state).getTransitionCaches()
        : TransitionCaches.getNoOp();
  }

  public TransitionCaches getTransitionCaches() {
    return transitionCaches;
  }
}
