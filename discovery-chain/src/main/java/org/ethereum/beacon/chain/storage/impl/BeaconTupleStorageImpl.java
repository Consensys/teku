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

package org.ethereum.beacon.chain.storage.impl;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.ethereum.beacon.chain.BeaconTuple;
import org.ethereum.beacon.chain.storage.BeaconBlockStorage;
import org.ethereum.beacon.chain.storage.BeaconStateStorage;
import org.ethereum.beacon.chain.storage.BeaconTupleStorage;
import org.ethereum.beacon.consensus.TransitionType;
import org.ethereum.beacon.consensus.transition.BeaconStateExImpl;
import tech.pegasys.artemis.ethereum.core.Hash32;

public class BeaconTupleStorageImpl implements BeaconTupleStorage {

  private final BeaconBlockStorage blockStorage;
  private final BeaconStateStorage stateStorage;

  public BeaconTupleStorageImpl(BeaconBlockStorage blockStorage, BeaconStateStorage stateStorage) {
    this.blockStorage = blockStorage;
    this.stateStorage = stateStorage;
  }

  @Override
  public Optional<BeaconTuple> get(@Nonnull Hash32 hash) {
    Objects.requireNonNull(hash);
    return blockStorage
        .get(hash)
        .map(
            block ->
                stateStorage
                    .get(block.getStateRoot())
                    .map(
                        state ->
                            BeaconTuple.of(
                                block, new BeaconStateExImpl(state, TransitionType.UNKNOWN)))
                    .orElseThrow(
                        () -> new IllegalStateException("State inconsistency for block " + block)));
  }

  @Override
  public void put(@Nonnull Hash32 hash, @Nonnull BeaconTuple tuple) {
    put(tuple);
  }

  @Override
  public void remove(@Nonnull Hash32 hash) {
    Objects.requireNonNull(hash);
    blockStorage.remove(hash);
    stateStorage.remove(hash);
  }

  @Override
  public void flush() {
    blockStorage.flush();
    stateStorage.flush();
  }

  @Override
  public boolean isEmpty() {
    return blockStorage.isEmpty();
  }

  @Override
  public void put(@Nonnull BeaconTuple tuple) {
    Objects.requireNonNull(tuple);

    blockStorage.put(tuple.getBlock());
    stateStorage.put(tuple.getBlock().getStateRoot(), tuple.getState());
  }
}
