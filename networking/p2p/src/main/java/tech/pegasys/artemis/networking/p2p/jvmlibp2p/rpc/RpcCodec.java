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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public final class RpcCodec {
  private static final Bytes SUCCESS_RESPONSE_CODE = Bytes.of(0);

  private RpcCodec() {}

  /**
   * Encodes a message into a RPC request
   *
   * @param request the payload of the request
   * @return the encoded RPC message
   */
  public static <T extends SimpleOffsetSerializable> Bytes encodeRequest(T request) {
    return encodePayload(request);
  }

  public static <T extends SimpleOffsetSerializable> Bytes encodeSuccessfulResponse(T response) {
    return Bytes.concatenate(SUCCESS_RESPONSE_CODE, encodePayload(response));
  }

  private static <T extends SimpleOffsetSerializable> Bytes encodePayload(final T data) {
    final Bytes payload = SimpleOffsetSerializer.serialize(data);
    final Bytes header = writeVarInt(payload.size());
    return Bytes.concatenate(header, payload);
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

  /**
   * Decodes a RPC request into a payload.
   *
   * @param message the bytes of the message to read
   * @return the payload, decoded
   */
  public static <T> T decodeRequest(Bytes message, Class<T> clazz) {
    try {
      final CodedInputStream in = CodedInputStream.newInstance(message.toArrayUnsafe());
      final int expectedLength = in.readRawVarint32();
      final Bytes payload = Bytes.wrap(in.readRawBytes(expectedLength));
      return SimpleOffsetSerializer.deserialize(payload, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> Response<T> decodeResponse(Bytes message, Class<T> clazz) {
    checkArgument(!message.isEmpty(), "Cannot decode an empty response");
    final byte responseCode = message.get(0);
    return new Response<>(responseCode, decodeRequest(message.slice(1), clazz));
  }
}
