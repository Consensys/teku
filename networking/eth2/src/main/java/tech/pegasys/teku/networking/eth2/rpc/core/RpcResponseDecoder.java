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

import static tech.pegasys.teku.infrastructure.unsigned.ByteUtil.toByteExactUnsigned;
import static tech.pegasys.teku.infrastructure.unsigned.ByteUtil.toUnsignedInt;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.SUCCESS_RESPONSE_CODE;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.PayloadTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.RpcErrorMessage;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;

/**
 * Responsible for decoding a stream of responses to a single rpc request
 *
 * @param <T>
 */
public class RpcResponseDecoder<T> {
  private Optional<Integer> respCodeMaybe = Optional.empty();
  private Optional<RpcByteBufDecoder<T>> payloadDecoder = Optional.empty();
  private Optional<RpcByteBufDecoder<RpcErrorMessage>> errorDecoder = Optional.empty();
  private final Class<T> responseType;
  private final RpcEncoding encoding;

  public RpcResponseDecoder(Class<T> responseType, RpcEncoding encoding) {
    this.responseType = responseType;
    this.encoding = encoding;
  }

  public List<T> decodeNextResponses(final ByteBuf data) throws RpcException {
    List<T> ret = new ArrayList<>();
    while (true) {
      Optional<T> responseMaybe = decodeNextResponse(data);
      if (responseMaybe.isPresent()) {
        ret.add(responseMaybe.get());
      } else {
        break;
      }
    }

    return ret;
  }

  private Optional<T> decodeNextResponse(final ByteBuf data) throws RpcException {
    if (!data.isReadable()) {
      return Optional.empty();
    }

    if (respCodeMaybe.isEmpty()) {
      respCodeMaybe = Optional.of(toUnsignedInt(data.readByte()));
    }
    int respCode = respCodeMaybe.get();

    if (respCode == SUCCESS_RESPONSE_CODE) {
      if (payloadDecoder.isEmpty()) {
        payloadDecoder = Optional.of(encoding.createDecoder(responseType));
      }
      Optional<T> ret = payloadDecoder.get().decodeOneMessage(data);
      if (ret.isPresent()) {
        respCodeMaybe = Optional.empty();
        payloadDecoder = Optional.empty();
      }
      return ret;
    } else {
      if (errorDecoder.isEmpty()) {
        errorDecoder = Optional.of(encoding.createDecoder(RpcErrorMessage.class));
      }
      Optional<RpcException> rpcException =
          errorDecoder
              .get()
              .decodeOneMessage(data)
              .map(errorMessage -> new RpcException(toByteExactUnsigned(respCode), errorMessage));
      if (rpcException.isPresent()) {
        respCodeMaybe = Optional.empty();
        errorDecoder = Optional.empty();
        throw rpcException.get();
      } else {
        return Optional.empty();
      }
    }
  }

  public void close() {
    payloadDecoder.ifPresent(ByteBufDecoder::close);
    errorDecoder.ifPresent(ByteBufDecoder::close);
  }

  public void complete() throws RpcException {
    try {
      if (payloadDecoder.isPresent()) {
        payloadDecoder.get().complete();
        payloadDecoder = Optional.empty();
      }
    } finally {
      if (errorDecoder.isPresent()) {
        errorDecoder.get().complete();
        errorDecoder = Optional.empty();
      }
    }
    if (respCodeMaybe.isPresent()) {
      throw new PayloadTruncatedException();
    }
  }
}
