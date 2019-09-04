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

package tech.pegasys.artemis.statetransition;

import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;

public class GenesisStateEvent {

  private BeaconStateWithCache genesisState;
  private BeaconBlock genesisBlock;

  public GenesisStateEvent(BeaconStateWithCache state, BeaconBlock block) {
    genesisState = state;
    genesisBlock = block;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public BeaconStateWithCache getGenesisState() {
    return genesisState;
  }

  public BeaconBlock getGenesisBlock() {
    return genesisBlock;
  }
}
