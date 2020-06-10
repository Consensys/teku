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

import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.SUCCESS_RESPONSE_CODE;
import static tech.pegasys.teku.util.bytes.ByteUtil.toByteExactUnsigned;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;

/**
 * Responsible for decoding a stream of responses to a single rpc request
 *
 * @param <T>
 */
public class RpcResponseDecoder<T> {
  private static final Logger LOG = LogManager.getLogger();

  private Optional<Integer> respCodeMaybe = Optional.empty();
  private final RpcByteBufDecoder<T> payloadDecoder;
  private final RpcByteBufDecoder<String> errorDecoder;

  protected RpcResponseDecoder(final Class<T> responseType, final RpcEncoding encoding) {
    this.payloadDecoder = encoding.createDecoder(responseType);
    this.errorDecoder = encoding.createDecoder(String.class);
  }

  public Optional<T> decodeNextResponse(final ByteBuf data) throws RpcException {
    return decodeNextResponse(data, Optional.empty());
  }

  public Optional<T> decodeNextResponse(
      final ByteBuf data, final FirstByteReceivedListener firstByteReceivedListener)
      throws RpcException {
    return decodeNextResponse(data, Optional.of(firstByteReceivedListener));
  }

  private Optional<T> decodeNextResponse(
      final ByteBuf data, Optional<FirstByteReceivedListener> firstByteListener)
      throws RpcException {
    firstByteListener.ifPresent(FirstByteReceivedListener::onFirstByteReceived);

    if (data.readableBytes() < 1) {
      return Optional.empty();
    }

    if (respCodeMaybe.isEmpty()) {
      respCodeMaybe = Optional.of((int) data.readByte());
    }
    int respCode = respCodeMaybe.get();

    if (respCode == SUCCESS_RESPONSE_CODE) {
      Optional<T> ret = payloadDecoder.decodeOneMessage(data);
      if (ret.isPresent()) {
        respCodeMaybe = Optional.empty();
      }
      return ret;
    } else {
      Optional<RpcException> rpcException = decodeError(data, respCode);
      if (rpcException.isPresent()) {
        respCodeMaybe = Optional.empty();
        throw rpcException.get();
      } else {
        return Optional.empty();
      }
    }
  }

  private Optional<RpcException> decodeError(final ByteBuf input, final int statusCode) throws RpcException {
    return errorDecoder.decodeOneMessage(input).map(errorMessage ->
            new RpcException(toByteExactUnsigned(statusCode), errorMessage)
        );
  }

  public void complete() throws RpcException {
    payloadDecoder.complete();
    errorDecoder.complete();
    if (respCodeMaybe.isPresent()) {
      throw RpcException.PAYLOAD_TRUNCATED;
    }
  }

  public interface FirstByteReceivedListener {
    void onFirstByteReceived();
  }
}
