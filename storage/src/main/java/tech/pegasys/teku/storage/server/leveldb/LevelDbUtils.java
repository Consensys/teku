/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.leveldb;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.unsigned.ByteUtil.toByteExact;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreChunkedVariable;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreUnchunkedVariable;

class LevelDbUtils {

  /** There's no default column in LevelDB so we use a -1 column prefix to store variables. */
  private static final byte VARIABLE_COLUMN_PREFIX = -1;

  static byte[] getKeyAfterColumn(final KvStoreColumn<?, ?> column) {
    final byte[] keyAfterColumn = column.getId().toArray();
    keyAfterColumn[keyAfterColumn.length - 1]++;
    return keyAfterColumn;
  }

  static byte[] getVariableKey(final KvStoreUnchunkedVariable<?> variable) {
    final byte[] suffix = variable.getId().toArrayUnsafe();
    final byte[] key = new byte[suffix.length + 1];
    // All 1s in binary so right at the end of the index.
    key[0] = VARIABLE_COLUMN_PREFIX;
    System.arraycopy(suffix, 0, key, 1, suffix.length);
    return key;
  }

  static byte[] getChunkedVariableKey(final KvStoreChunkedVariable<?> variable, final int chunkId) {
    final byte[] idSuffix = variable.getId().toArrayUnsafe();
    final byte[] key = new byte[idSuffix.length + 2];
    // All 1s in binary so right at the end of the index.
    key[0] = VARIABLE_COLUMN_PREFIX;
    System.arraycopy(idSuffix, 0, key, 1, idSuffix.length);
    // last is chunk id
    key[key.length - 1] = toByteExact(chunkId);
    return key;
  }

  static byte[] getChunkedVariableChunksKey(final KvStoreChunkedVariable<?> variable) {
    final byte[] suffix = variable.getId().toArrayUnsafe();
    final byte[] key = new byte[suffix.length + 1];
    // All 1s in binary so right at the end of the index.
    key[0] = VARIABLE_COLUMN_PREFIX;
    System.arraycopy(suffix, 0, key, 1, suffix.length);
    return key;
  }

  static <K, V> byte[] getColumnKey(final KvStoreColumn<K, V> column, final K key) {
    final byte[] suffix = column.getKeySerializer().serialize(key);
    checkArgument(suffix.length > 0, "Empty item key detected for serialization of %s", key);
    return getColumnKey(column, suffix);
  }

  static <K, V> byte[] getColumnKey(final KvStoreColumn<K, V> column, final byte[] key) {
    final byte[] prefix = column.getId().toArrayUnsafe();
    return concat(prefix, key);
  }

  static <K, V> boolean isFromColumn(final KvStoreColumn<K, V> column, final byte[] key) {
    final byte[] prefix = column.getId().toArrayUnsafe();
    if (key.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (prefix[i] != key[i]) {
        return false;
      }
    }
    return true;
  }

  private static byte[] concat(final byte[] a, final byte[] b) {
    final byte[] result = Arrays.copyOf(a, a.length + b.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    return result;
  }

  static <K, V> Optional<ColumnEntry<K, V>> asOptionalColumnEntry(
      final KvStoreColumn<K, V> column, final Map.Entry<byte[], byte[]> entry) {
    return Optional.of(asColumnEntry(column, entry));
  }

  static <K, V> ColumnEntry<K, V> asColumnEntry(
      final KvStoreColumn<K, V> column, final Map.Entry<byte[], byte[]> entry) {
    return ColumnEntry.create(
        deserializeKey(column, entry.getKey()),
        column.getValueSerializer().deserialize(entry.getValue()));
  }

  static ColumnEntry<byte[], byte[]> asRawColumnEntry(
      final KvStoreColumn<?, ?> column, final Map.Entry<byte[], byte[]> entry) {
    return ColumnEntry.create(removeKeyPrefix(column, entry.getKey()), entry.getValue());
  }

  static <K, V> K deserializeKey(final KvStoreColumn<K, V> column, final byte[] key) {
    final byte[] keyBytes = removeKeyPrefix(column, key);
    return column.getKeySerializer().deserialize(keyBytes);
  }

  static byte[] removeKeyPrefix(final KvStoreColumn<?, ?> column, final byte[] key) {
    return Arrays.copyOfRange(key, column.getId().size(), key.length);
  }
}
