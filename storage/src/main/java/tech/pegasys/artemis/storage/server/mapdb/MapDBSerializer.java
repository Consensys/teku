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

package tech.pegasys.artemis.storage.server.mapdb;

import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.ssz.sos.SimpleOffsetSerializable;

public class MapDBSerializer<T extends SimpleOffsetSerializable> implements Serializer<T> {

  private Class<? extends T> classInfo;

  public MapDBSerializer(Class<? extends T> classInformation) {
    this.classInfo = classInformation;
  }

  @Override
  public void serialize(DataOutput2 out, T value) throws IOException {
    final byte[] data = SimpleOffsetSerializer.serialize(value).toArrayUnsafe();
    Serializer.BYTE_ARRAY.serialize(out, data);
  }

  @Override
  public T deserialize(DataInput2 in, int available) throws IOException {
    final byte[] data = Serializer.BYTE_ARRAY.deserialize(in, available);
    return SimpleOffsetSerializer.deserialize(Bytes.wrap(data), classInfo);
  }

  @Override
  public int fixedSize() {
    return Serializer.BYTE_ARRAY.fixedSize();
  }

  @Override
  public boolean isTrusted() {
    return Serializer.BYTE_ARRAY.isTrusted();
  }
}
