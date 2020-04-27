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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer;

public class RocksDbVariableTest {
  private final RocksDbSerializer<UnsignedLong> serializer =
      RocksDbSerializer.UNSIGNED_LONG_SERIALIZER;

  @Test
  public void create_idTooLarge() {
    assertThatThrownBy(() -> RocksDbVariable.create(128, serializer))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid id supplied");
  }

  @Test
  public void create_idTooSmall() {
    assertThatThrownBy(() -> RocksDbVariable.create(-129, serializer))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid id supplied");
  }
}
