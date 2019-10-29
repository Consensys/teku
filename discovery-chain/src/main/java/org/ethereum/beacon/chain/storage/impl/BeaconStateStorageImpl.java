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
import org.ethereum.beacon.chain.storage.BeaconStateStorage;
import org.ethereum.beacon.consensus.hasher.ObjectHasher;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.state.BeaconStateImpl;
import org.ethereum.beacon.db.Database;
import org.ethereum.beacon.db.source.CodecSource;
import org.ethereum.beacon.db.source.DataSource;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class BeaconStateStorageImpl implements BeaconStateStorage {

  private final ObjectHasher<Hash32> objectHasher;
  private final DataSource<Hash32, BeaconState> source;

  public BeaconStateStorageImpl(
      DataSource<Hash32, BeaconState> source, ObjectHasher<Hash32> objectHasher) {
    this.source = source;
    this.objectHasher = objectHasher;
  }

  @Override
  public Optional<BeaconState> get(@Nonnull Hash32 key) {
    Objects.requireNonNull(key);
    return source.get(key);
  }

  @Override
  public void put(@Nonnull Hash32 key, @Nonnull BeaconState value) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    source.put(key, value);
  }

  @Override
  public void put(BeaconState state) {
    this.put(objectHasher.getHash(state), state);
  }

  @Override
  public void remove(@Nonnull Hash32 key) {
    Objects.requireNonNull(key);
    source.remove(key);
  }

  @Override
  public void flush() {
    // nothing to be done here. No cached data in this implementation
  }

  public static BeaconStateStorageImpl create(
      Database database, ObjectHasher<Hash32> objectHasher, SerializerFactory serializerFactory) {
    DataSource<BytesValue, BytesValue> backingSource = database.createStorage("beacon-state");
    DataSource<Hash32, BeaconState> stateSource =
        new CodecSource<>(
            backingSource,
            key -> key,
            serializerFactory.getSerializer(BeaconState.class),
            bytes -> serializerFactory.getDeserializer(BeaconStateImpl.class).apply(bytes));
    return new BeaconStateStorageImpl(stateSource, objectHasher);
  }
}
