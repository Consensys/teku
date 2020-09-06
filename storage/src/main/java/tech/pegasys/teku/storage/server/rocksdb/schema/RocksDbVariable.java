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

public class RocksDbVariable<TValue> {
  private final Bytes id;
  private final RocksDbSerializer<TValue> serializer;

  private RocksDbVariable(final byte[] id, final RocksDbSerializer<TValue> serializer) {
    this.id = Bytes.wrap(id);
    this.serializer = serializer;
  }

  public static <T> RocksDbVariable<T> create(final int id, final RocksDbSerializer<T> serializer) {
    final byte byteId = toByteExact(id);
    return new RocksDbVariable<T>(new byte[] {byteId}, serializer);
  }

  public Bytes getId() {
    return id;
  }

  public RocksDbSerializer<TValue> getSerializer() {
    return serializer;
  }
}
