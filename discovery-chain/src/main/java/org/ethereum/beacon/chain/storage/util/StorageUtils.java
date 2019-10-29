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

package org.ethereum.beacon.chain.storage.util;

import org.ethereum.beacon.chain.BeaconTuple;
import org.ethereum.beacon.chain.storage.BeaconChainStorage;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.state.Checkpoint;
import tech.pegasys.artemis.ethereum.core.Hash32;

/** Utility functions to initialize storage from an initial state. */
public class StorageUtils {
  /**
   * Creates a BeaconTuple consisting of the initialState and corresponding block. Currently, the
   * block is empty, but could be re-constructed from the state's block header, in general.
   */
  public static BeaconTuple createInitialBeaconTuple(
      BeaconChainSpec spec, BeaconStateEx initialState) {
    BeaconBlock initialGenesis = spec.get_empty_block();
    Hash32 initialStateRoot = spec.hash_tree_root(initialState);
    BeaconBlock genesis = initialGenesis.withStateRoot(initialStateRoot);
    return BeaconTuple.of(genesis, initialState);
  }

  /**
   * An utility to properly initialize a storage with a specified initial state. Supports only
   * initial state currently. Could be extended in theory, to support finalized states.
   */
  public static void initializeStorage(
      BeaconChainStorage storage, BeaconChainSpec spec, BeaconStateEx initialState) {
    assert storage.getTupleStorage().isEmpty();
    BeaconTuple tuple = createInitialBeaconTuple(spec, initialState);
    Hash32 genesisRoot = spec.signing_root(tuple.getBlock());
    storage.getStateStorage().put(tuple.getBlock().getStateRoot(), tuple.getState());
    storage.getBlockStorage().put(genesisRoot, tuple.getBlock());
    storage
        .getJustifiedStorage()
        .set(new Checkpoint(initialState.getCurrentJustifiedCheckpoint().getEpoch(), genesisRoot));
    storage
        .getFinalizedStorage()
        .set(new Checkpoint(initialState.getFinalizedCheckpoint().getEpoch(), genesisRoot));
  }
}
