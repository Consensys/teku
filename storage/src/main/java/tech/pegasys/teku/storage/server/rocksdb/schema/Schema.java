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

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;

public interface Schema {
  Bytes DEFAULT_COLUMN_ID = Bytes.wrap("default".getBytes(StandardCharsets.UTF_8));

  static Stream<RocksDbColumn<?, ?>> streamColumns(Class<? extends Schema> schema) {
    return Arrays.stream(schema.getDeclaredFields())
        .filter(f -> (f.getModifiers() & Modifier.STATIC) > 0)
        .filter(f -> f.getType() == RocksDbColumn.class)
        .map(
            f -> {
              try {
                return (RocksDbColumn<?, ?>) f.get(null);
              } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
              }
            });
  }
}
