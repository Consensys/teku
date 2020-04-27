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

package tech.pegasys.teku.networking.eth2.rpc.core;

import io.netty.buffer.ByteBuf;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;

public class ResponseRpcDecoder<T> {

  public static final byte SUCCESS_RESPONSE_CODE = 0;
  private static final int STATUS_CODE_LENGTH = 1;
  protected final MessageBuffer buffer = new MessageBuffer();

  private final Consumer<T> callback;
  private final Class<T> dataType;
  private final RpcEncoding encoding;

  protected ResponseRpcDecoder(final Consumer<T> callback, final Eth2RpcMethod<?, T> method) {
    this.callback = callback;
    this.dataType = method.getResponseType();
    this.encoding = method.getEncoding();
  }

  public void onDataReceived(final ByteBuf bytes) throws RpcException {
    buffer.appendData(bytes);
    try {
      buffer.consumeData(this::consumeData);
    } catch (final RpcException e) {
      // Discard remaining data to avoid close throwing exceptions later.
      buffer.close();
      throw e;
    }
  }

  private int consumeData(final Bytes currentData) throws RpcException {
    final byte statusCode = currentData.get(0);
    Bytes encodedMessageData = currentData.slice(STATUS_CODE_LENGTH);
    final OptionalInt encodingSectionLength = encoding.getMessageLength(encodedMessageData);
    if (encodingSectionLength.isEmpty()) {
      // Too soon to calculate the next message length
      return 0;
    }
    final int encodedMessageLength = encodingSectionLength.getAsInt();
    final int totalMessageLength = encodedMessageLength + STATUS_CODE_LENGTH;

    if (currentData.size() < totalMessageLength) {
      // Still waiting for more data
      return 0;
    }
    encodedMessageData = encodedMessageData.slice(0, encodedMessageLength);
    if (statusCode == SUCCESS_RESPONSE_CODE) {
      final T message = encoding.decode(encodedMessageData, dataType);
      callback.accept(message);
    } else {
      final String errorMessage = encoding.decode(encodedMessageData, String.class);
      throw new RpcException(statusCode, errorMessage);
    }
    return totalMessageLength;
  }

  public void close() throws RpcException {
    final boolean consumedAllData = buffer.isEmpty();
    buffer.close();
    if (!consumedAllData) {
      throw RpcException.INCORRECT_LENGTH_ERROR;
    }
  }

  /** Close ignoring any unconsumed data */
  public void closeSilently() {
    try {
      close();
    } catch (RpcException e) {
      // Ignore
    }
  }
}
