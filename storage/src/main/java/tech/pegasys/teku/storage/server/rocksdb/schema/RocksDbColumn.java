/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.rocksdb.schema;

import static tech.pegasys.teku.infrastructure.unsigned.ByteUtil.toByteExact;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer;

public class RocksDbColumn<TKey, TValue> {
  private final Bytes id;
  private final RocksDbSerializer<TKey> keySerializer;
  private final RocksDbSerializer<TValue> valueSerializer;

  private RocksDbColumn(
      final byte[] id,
      final RocksDbSerializer<TKey> keySerializer,
      final RocksDbSerializer<TValue> valueSerializer) {
    this.id = Bytes.wrap(id);
    this.keySerializer = keySerializer;
    this.valueSerializer = valueSerializer;
  }

  public static <K, V> RocksDbColumn<K, V> create(
      final int id,
      final RocksDbSerializer<K> keySerializer,
      final RocksDbSerializer<V> valueSerializer) {
    final byte byteId = toByteExact(id);
    return new RocksDbColumn<>(new byte[] {byteId}, keySerializer, valueSerializer);
  }

  public Bytes getId() {
    return id;
  }

  public RocksDbSerializer<TKey> getKeySerializer() {
    return keySerializer;
  }

  public RocksDbSerializer<TValue> getValueSerializer() {
    return valueSerializer;
  }
}
