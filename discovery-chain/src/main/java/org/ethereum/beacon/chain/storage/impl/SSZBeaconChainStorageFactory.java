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
import org.ethereum.beacon.consensus.hasher.SSZObjectHasher;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.db.Database;
import org.ethereum.beacon.db.source.DataSource;
import org.ethereum.beacon.db.source.SingleValueSource;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * An implementation which passes {@link SSZObjectHasher} and {@link SSZSerializerFactory} to {@link
 * BeaconChainStorage} instance.
 */
public class SSZBeaconChainStorageFactory implements BeaconChainStorageFactory {
  private final ObjectHasher<Hash32> objectHasher;
  private final SerializerFactory serializerFactory;

  public SSZBeaconChainStorageFactory(
      ObjectHasher<Hash32> objectHasher, SerializerFactory serializerFactory) {
    this.objectHasher = objectHasher;
    this.serializerFactory = serializerFactory;
  }

  @Override
  public BeaconChainStorage create(Database database) {
    BeaconBlockStorage blockStorage =
        BeaconBlockStorageImpl.create(database, objectHasher, serializerFactory);
    BeaconStateStorage stateStorage =
        BeaconStateStorageImpl.create(database, objectHasher, serializerFactory);
    BeaconTupleStorage tupleStorage = new BeaconTupleStorageImpl(blockStorage, stateStorage);

    SingleValueSource<Checkpoint> justifiedStorage =
        createSingleValueStorage(database, "justified-hash", Checkpoint.class);
    SingleValueSource<Checkpoint> finalizedStorage =
        createSingleValueStorage(database, "finalized-hash", Checkpoint.class);

    return new BeaconChainStorageImpl(
        database,
        blockStorage,
        new DelegateBlockHeaderStorageImpl(blockStorage, objectHasher),
        stateStorage,
        tupleStorage,
        justifiedStorage,
        finalizedStorage);
  }

  private <U> SingleValueSource<U> createSingleValueStorage(
      Database database, String name, Class<? extends U> type) {
    DataSource<BytesValue, BytesValue> justifiedStorageSource = database.createStorage(name);
    return SingleValueSource.fromDataSource(
        justifiedStorageSource,
        BytesValue.wrap(name.getBytes()),
        serializerFactory.getSerializer(type),
        serializerFactory.getDeserializer(type));
  }
}
