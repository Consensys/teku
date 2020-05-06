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

package tech.pegasys.teku.storage.server.rocksdb.serialization;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class SszSerializer<T extends SimpleOffsetSerializable> implements RocksDbSerializer<T> {

  private final Class<? extends T> classInfo;

  public <TImplementation extends T> SszSerializer(final Class<TImplementation> classInfo) {
    this.classInfo = classInfo;
  }

  @Override
  public T deserialize(final byte[] data) {
    return SimpleOffsetSerializer.deserialize(Bytes.wrap(data), classInfo);
  }

  @Override
  public byte[] serialize(final T value) {
    return SimpleOffsetSerializer.serialize(value).toArrayUnsafe();
  }
}
