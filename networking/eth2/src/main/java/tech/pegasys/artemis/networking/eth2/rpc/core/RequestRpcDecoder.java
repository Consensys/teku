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

package tech.pegasys.artemis.networking.eth2.rpc.core;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;

class RequestRpcDecoder<T> {

  protected final MessageBuffer buffer = new MessageBuffer();
  private final Class<T> dataType;
  private final RpcEncoding encoding;
  private Optional<T> result = Optional.empty();

  protected RequestRpcDecoder(final RpcMethod<T, ?> method) {
    this.dataType = method.getRequestType();
    this.encoding = method.getEncoding();
  }

  public Optional<T> onDataReceived(final ByteBuf byteBuf) throws RpcException {
    buffer.appendData(byteBuf);
    buffer.consumeData(this::consumeData);
    if (result.isPresent() && !buffer.isEmpty()) {
      throw RpcException.INCORRECT_LENGTH_ERROR;
    }
    return result;
  }

  public void close() {
    buffer.close();
  }

  private int consumeData(final Bytes currentData) throws RpcException {
    Bytes encodedMessageData = currentData;

    final OptionalInt encodingSectionLength = encoding.getMessageLength(encodedMessageData);
    if (encodingSectionLength.isEmpty()) {
      // Too soon to calculate the next message length
      return 0;
    }
    final int encodedMessageLength = encodingSectionLength.getAsInt();
    if (currentData.size() < encodedMessageLength) {
      // Still waiting for more data
      return 0;
    }

    encodedMessageData = encodedMessageData.slice(0, encodedMessageLength);
    final T message = encoding.decode(encodedMessageData, dataType);
    result = Optional.of(message);
    return encodedMessageLength;
  }
}
