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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.encodings;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class SszEncoding implements RpcEncoding {

  @Override
  public <T extends SimpleOffsetSerializable> Bytes encodeMessage(final T data) {
    final Bytes payload = SimpleOffsetSerializer.serialize(data);
    final Bytes header = writeVarInt(payload.size());
    return Bytes.concatenate(header, payload);
  }

  @Override
  public <T> T decodeMessage(final Bytes message, final Class<T> clazz) {
    try {
      final CodedInputStream in = CodedInputStream.newInstance(message.toArrayUnsafe());
      final int expectedLength = in.readRawVarint32();
      final Bytes payload = Bytes.wrap(in.readRawBytes(expectedLength));
      return SimpleOffsetSerializer.deserialize(payload, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getName() {
    return "ssz";
  }

  private static Bytes writeVarInt(final int value) {
    try {
      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      final CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(output);
      codedOutputStream.writeUInt32NoTag(value);
      codedOutputStream.flush();
      return Bytes.wrap(output.toByteArray());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
