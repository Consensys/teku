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
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.GroupSerializerObjectArray;

public class Bytes32Serializer extends GroupSerializerObjectArray<Bytes32> {

  @Override
  public void serialize(DataOutput2 out, Bytes32 value) throws IOException {
    out.write(value.toArrayUnsafe());
  }

  @Override
  public Bytes32 deserialize(DataInput2 in, int available) throws IOException {
    final byte[] data = new byte[Bytes32.SIZE];
    in.readFully(data);
    return Bytes32.wrap(data);
  }

  @Override
  public boolean isTrusted() {
    return true;
  }

  @Override
  public int fixedSize() {
    return Bytes32.SIZE;
  }
}
