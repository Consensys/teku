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

package tech.pegasys.artemis.storage;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.io.Serializable;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

public class LatestMessage {

  private UnsignedLong epoch;
  private Bytes32 root;

  public LatestMessage(UnsignedLong epoch, Bytes32 root) {
    this.epoch = epoch;
    this.root = root;
  }

  public UnsignedLong getEpoch() {
    return epoch;
  }

  public void setEpoch(UnsignedLong epoch) {
    this.epoch = epoch;
  }

  public Bytes32 getRoot() {
    return root;
  }

  public void setRoot(Bytes32 root) {
    this.root = root;
  }

  public static LatestMessage fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new LatestMessage(
                UnsignedLong.fromLongBits(reader.readUInt64()),
                Bytes32.wrap(reader.readFixedBytes(32))));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(epoch.longValue());
          writer.writeFixedBytes(root);
        });
  }

  public static class LatestMessageSerializer implements Serializer<LatestMessage>, Serializable {
    @Override
    public void serialize(DataOutput2 out, LatestMessage checkpoint) throws IOException {
      out.writeChars(checkpoint.toBytes().toHexString());
    }

    @Override
    public LatestMessage deserialize(DataInput2 in, int available) throws IOException {
      return LatestMessage.fromBytes(Bytes.fromHexString(in.readLine()));
    }
  }
}
