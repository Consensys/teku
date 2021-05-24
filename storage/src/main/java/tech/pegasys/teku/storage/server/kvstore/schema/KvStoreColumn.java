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

package tech.pegasys.teku.storage.server.kvstore.schema;

import static tech.pegasys.teku.infrastructure.unsigned.ByteUtil.toByteExact;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer;

public class KvStoreColumn<TKey, TValue> {
  private final Bytes id;
  private final KvStoreSerializer<TKey> keySerializer;
  private final KvStoreSerializer<TValue> valueSerializer;

  private KvStoreColumn(
      final byte[] id,
      final KvStoreSerializer<TKey> keySerializer,
      final KvStoreSerializer<TValue> valueSerializer) {
    this.id = Bytes.wrap(id);
    this.keySerializer = keySerializer;
    this.valueSerializer = valueSerializer;
  }

  public static <K, V> KvStoreColumn<K, V> create(
      final int id,
      final KvStoreSerializer<K> keySerializer,
      final KvStoreSerializer<V> valueSerializer) {
    final byte byteId = toByteExact(id);
    return new KvStoreColumn<>(new byte[] {byteId}, keySerializer, valueSerializer);
  }

  public Bytes getId() {
    return id;
  }

  public KvStoreSerializer<TKey> getKeySerializer() {
    return keySerializer;
  }

  public KvStoreSerializer<TValue> getValueSerializer() {
    return valueSerializer;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final KvStoreColumn<?, ?> that = (KvStoreColumn<?, ?>) o;
    return Objects.equals(id, that.id)
        && Objects.equals(keySerializer, that.keySerializer)
        && Objects.equals(valueSerializer, that.valueSerializer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, keySerializer, valueSerializer);
  }
}
