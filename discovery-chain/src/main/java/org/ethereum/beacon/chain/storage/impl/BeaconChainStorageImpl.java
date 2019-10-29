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

import org.ethereum.beacon.chain.storage.BeaconBlockStorage;
import org.ethereum.beacon.chain.storage.BeaconChainStorage;
import org.ethereum.beacon.chain.storage.BeaconStateStorage;
import org.ethereum.beacon.chain.storage.BeaconTupleStorage;
import org.ethereum.beacon.core.BeaconBlockHeader;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.db.Database;
import org.ethereum.beacon.db.source.DataSource;
import org.ethereum.beacon.db.source.SingleValueSource;
import tech.pegasys.artemis.ethereum.core.Hash32;

/** A default implementation of {@link BeaconChainStorage}. */
public class BeaconChainStorageImpl implements BeaconChainStorage {

  private final Database database;
  private final BeaconBlockStorage blockStorage;
  private final DataSource<Hash32, BeaconBlockHeader> blockHeaderStorage;
  private final BeaconStateStorage stateStorage;
  private final BeaconTupleStorage tupleStorage;
  private final SingleValueSource<Checkpoint> justifiedStorage;
  private final SingleValueSource<Checkpoint> finalizedStorage;

  public BeaconChainStorageImpl(
      Database database,
      BeaconBlockStorage blockStorage,
      DataSource<Hash32, BeaconBlockHeader> blockHeaderStorage,
      BeaconStateStorage stateStorage,
      BeaconTupleStorage tupleStorage,
      SingleValueSource<Checkpoint> justifiedStorage,
      SingleValueSource<Checkpoint> finalizedStorage) {
    this.database = database;
    this.blockStorage = blockStorage;
    this.blockHeaderStorage = blockHeaderStorage;
    this.stateStorage = stateStorage;
    this.tupleStorage = tupleStorage;
    this.justifiedStorage = justifiedStorage;
    this.finalizedStorage = finalizedStorage;
  }

  @Override
  public BeaconBlockStorage getBlockStorage() {
    return blockStorage;
  }

  @Override
  public DataSource<Hash32, BeaconBlockHeader> getBlockHeaderStorage() {
    return blockHeaderStorage;
  }

  @Override
  public BeaconStateStorage getStateStorage() {
    return stateStorage;
  }

  @Override
  public BeaconTupleStorage getTupleStorage() {
    return tupleStorage;
  }

  @Override
  public SingleValueSource<Checkpoint> getJustifiedStorage() {
    return justifiedStorage;
  }

  @Override
  public SingleValueSource<Checkpoint> getFinalizedStorage() {
    return finalizedStorage;
  }

  @Override
  public void commit() {
    tupleStorage.flush();
    database.commit();
  }
}
