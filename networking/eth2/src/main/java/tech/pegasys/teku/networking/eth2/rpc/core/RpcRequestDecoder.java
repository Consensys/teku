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
import java.util.Optional;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;

/**
 * A decoder responsible for handling a single rpc request
 *
 * @param <T> The type of request to expect
 */
public class RpcRequestDecoder<T extends RpcRequest> {
  private final RpcByteBufDecoder<T> decoder;
  private boolean complete;

  public RpcRequestDecoder(final Class<T> requestType, final RpcEncoding encoding) {
    this.decoder = encoding.createDecoder(requestType);
  }

  public Optional<T> decodeRequest(final ByteBuf input) throws RpcException {
    if (complete) {
      if (input.isReadable()) {
        throw RpcException.EXTRA_DATA_APPENDED;
      } else {
        return Optional.empty();
      }
    }
    final Optional<T> request = decoder.decodeOneMessage(input);

    if (request.isPresent()) {
      // Check for extra bytes remaining
      if (input.readableBytes() != 0) {
        throw RpcException.EXTRA_DATA_APPENDED;
      }
      complete = true;
    }

    return request;
  }

  public void complete() throws RpcException {
    decoder.complete();
    if (!complete) throw RpcException.PAYLOAD_TRUNCATED;
  }
}
