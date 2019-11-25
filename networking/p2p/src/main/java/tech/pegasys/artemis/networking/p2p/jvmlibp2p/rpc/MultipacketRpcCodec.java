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

import io.netty.buffer.ByteBuf;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.encodings.RpcEncoding;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class MultipacketRpcCodec<T extends SimpleOffsetSerializable> {

  public static final byte SUCCESS_RESPONSE_CODE = 0;
  private static final int STATUS_CODE_LENGTH = 1;
  private List<ByteBuf> buffers = new LinkedList<>();
  private Bytes currentData = Bytes.EMPTY;
  private final Consumer<T> callback;
  private final Class<T> dataType;
  private final RpcEncoding encoding;

  public MultipacketRpcCodec(
      final Consumer<T> callback, final Class<T> dataType, final RpcEncoding encoding) {
    this.callback = callback;
    this.dataType = dataType;
    this.encoding = encoding;
  }

  public void onPacketReceived(final ByteBuf data) throws RpcException {
    data.retain();
    buffers.add(data);
    currentData = Bytes.wrap(currentData, Bytes.wrapByteBuf(data));

    while (currentData.size() > 2) {
      final byte statusCode = currentData.get(0);
      Bytes encodedMessageData = currentData.slice(STATUS_CODE_LENGTH);
      final OptionalInt encodingSectionLength = encoding.getMessageLength(encodedMessageData);
      if (encodingSectionLength.isEmpty()) {
        // Too soon to calculate the next message length
        return;
      }
      final int encodedMessageLength = encodingSectionLength.getAsInt();
      final int totalMessageLength = encodedMessageLength + STATUS_CODE_LENGTH;

      if (currentData.size() < totalMessageLength) {
        // Still waiting for more data
        return;
      }
      encodedMessageData = encodedMessageData.slice(0, encodedMessageLength);
      if (statusCode == SUCCESS_RESPONSE_CODE) {
        final T message = encoding.decodeMessage(encodedMessageData, dataType);
        callback.accept(message);
      } else {
        final String errorMessage = encoding.decodeError(encodedMessageData);
        currentData = Bytes.EMPTY;
        throw new RpcException(statusCode, errorMessage);
      }

      trimParsedData(totalMessageLength);
    }
  }

  private void trimParsedData(final int totalMessageLength) {
    currentData = currentData.slice(totalMessageLength);
    int bytesToTrim = totalMessageLength;
    for (final Iterator<ByteBuf> i = buffers.iterator(); i.hasNext() && bytesToTrim > 0; ) {
      final ByteBuf buffer = i.next();
      if (buffer.capacity() <= bytesToTrim) {
        bytesToTrim -= buffer.capacity();
        i.remove();
        buffer.release();
      }
    }
  }

  public void close() throws RpcException {
    buffers.forEach(ByteBuf::release);
    if (currentData.size() > 0) {
      throw RpcException.INCORRECT_LENGTH_ERROR;
    }
  }
}
