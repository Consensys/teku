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
import org.ethereum.beacon.chain.storage.BeaconChainStorageFactory;
import org.ethereum.beacon.chain.storage.BeaconStateStorage;
import org.ethereum.beacon.chain.storage.BeaconTupleStorage;
import org.ethereum.beacon.consensus.hasher.ObjectHasher;
import org.ethereum.beacon.db.Database;
import org.ethereum.beacon.db.source.SingleValueSource;
import org.ethereum.beacon.db.source.impl.HashMapDataSource;
import org.ethereum.beacon.db.source.impl.HashMapHoleyList;
import tech.pegasys.artemis.ethereum.core.Hash32;

public class MemBeaconChainStorageFactory implements BeaconChainStorageFactory {
  private final ObjectHasher<Hash32> objectHasher;

  public MemBeaconChainStorageFactory(ObjectHasher<Hash32> objectHasher) {
    this.objectHasher = objectHasher;
  }

  @Override
  public BeaconChainStorage create(Database database) {
    BeaconBlockStorage blockStorage =
        new BeaconBlockStorageImpl(
            objectHasher, new HashMapDataSource<>(), new HashMapHoleyList<>());
    BeaconStateStorage stateStorage =
        new BeaconStateStorageImpl(new HashMapDataSource<>(), objectHasher);
    BeaconTupleStorage tupleStorage = new BeaconTupleStorageImpl(blockStorage, stateStorage);

    return new BeaconChainStorageImpl(
        database,
        blockStorage,
        new DelegateBlockHeaderStorageImpl(blockStorage, objectHasher),
        stateStorage,
        tupleStorage,
        SingleValueSource.memSource(),
        SingleValueSource.memSource());
  }
}
