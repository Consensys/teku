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
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.InvalidSSZTypeException;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcException;

public class SszEncoding implements RpcEncoding {
  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_CHUNK_SIZE = 1048576;
  // Any protobuf length requiring more bytes than this will also be bigger.
  private static final int MAXIMUM_VARINT_LENGTH = writeVarInt(MAX_CHUNK_SIZE).size();

  @Override
  public <T> Bytes encodeMessage(final T data) {
    final Bytes payload = RpcSszEncoder.encode(data);
    return encodeMessageWithLength(payload);
  }

  @Override
  public Bytes encodeError(final String errorMessage) {
    return encodeMessageWithLength(Bytes.wrap(errorMessage.getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public String decodeError(final Bytes message) throws RpcException {
    return decode(message, data -> new String(data.toArrayUnsafe(), StandardCharsets.UTF_8));
  }

  private Bytes encodeMessageWithLength(final Bytes payload) {
    final Bytes header = writeVarInt(payload.size());
    return Bytes.concatenate(header, payload);
  }

  @Override
  public <T> T decodeMessage(final Bytes message, final Class<T> clazz) throws RpcException {
    return decode(message, payload -> RpcSszEncoder.decode(payload, clazz));
  }

  private <T> T decode(final Bytes message, final Function<Bytes, T> parser) throws RpcException {
    try {
      final CodedInputStream in = CodedInputStream.newInstance(message.toArrayUnsafe());
      final int expectedLength;
      try {
        expectedLength = in.readRawVarint32();
      } catch (final InvalidProtocolBufferException e) {
        throw RpcException.MALFORMED_REQUEST_ERROR;
      }

      if (expectedLength > MAX_CHUNK_SIZE) {
        throw RpcException.CHUNK_TOO_LONG_ERROR;
      }

      final Bytes payload;
      try {
        payload = Bytes.wrap(in.readRawBytes(expectedLength));
      } catch (final InvalidProtocolBufferException e) {
        LOG.trace("Failed to parse SSZ message", e);
        throw RpcException.INCORRECT_LENGTH_ERROR;
      }

      if (!in.isAtEnd()) {
        LOG.trace("Rejecting SSZ message because actual message length exceeds specified length");
        throw RpcException.INCORRECT_LENGTH_ERROR;
      }

      final T parsedMessage;
      try {
        parsedMessage = parser.apply(payload);
      } catch (final InvalidSSZTypeException e) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Failed to parse network message: " + message, e);
        }
        throw RpcException.MALFORMED_REQUEST_ERROR;
      }

      return parsedMessage;
    } catch (IOException e) {
      LOG.error("Unexpected error while processing message: " + message, e);
      throw RpcException.SERVER_ERROR;
    }
  }

  @Override
  public String getName() {
    return "ssz";
  }

  @Override
  public OptionalInt getMessageLength(final Bytes message) throws RpcException {
    final OptionalInt maybePrefixLength = getLengthPrefixSize(message);
    if (maybePrefixLength.isEmpty()) {
      return OptionalInt.empty();
    }
    final int prefixLength = maybePrefixLength.getAsInt();
    final CodedInputStream in = CodedInputStream.newInstance(message.toArrayUnsafe());
    try {
      return OptionalInt.of(in.readRawVarint32() + prefixLength);
    } catch (final IOException e) {
      throw RpcException.MALFORMED_MESSAGE_LENGTH_ERROR;
    }
  }

  // Var int ends at first byte where (b & 0x80) == 0
  private OptionalInt getLengthPrefixSize(final Bytes message) throws RpcException {
    for (int i = 0; i < message.size() && i <= MAXIMUM_VARINT_LENGTH; i++) {
      if (i >= MAXIMUM_VARINT_LENGTH) {
        throw RpcException.CHUNK_TOO_LONG_ERROR;
      }
      if ((message.get(i) & 0x80) == 0) {
        return OptionalInt.of(i + 1);
      }
    }
    return OptionalInt.empty();
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
