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

package tech.pegasys.artemis.storage.utils;

import java.io.IOException;
import java.io.Serializable;
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

public class Bytes32Serializer implements Serializer<Bytes32>, Serializable {

  @Override
  public void serialize(DataOutput2 out, Bytes32 value) throws IOException {
    out.writeChars(value.toHexString());
  }

  @Override
  public Bytes32 deserialize(DataInput2 in, int available) throws IOException {
    Bytes32 returnVal = Bytes32.fromHexString(in.readLine());
    return returnVal;
  }
}
