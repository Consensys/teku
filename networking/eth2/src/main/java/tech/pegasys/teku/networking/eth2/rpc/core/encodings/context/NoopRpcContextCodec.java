/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.context;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.schema.SszSchema;

class NoopRpcContextCodec<TPayload extends SszData> implements RpcContextCodec<Bytes, TPayload> {
  private static final RpcByteBufDecoder<Bytes> DECODER = new EmptyContextDecoder();
  private final SszSchema<TPayload> schema;

  NoopRpcContextCodec(final SszSchema<TPayload> schema) {
    this.schema = schema;
  }

  @Override
  public RpcByteBufDecoder<Bytes> getContextDecoder() {
    return DECODER;
  }

  @Override
  public Bytes encodeContext(final TPayload responsePayload) {
    return Bytes.EMPTY;
  }

  @Override
  public Optional<SszSchema<TPayload>> getSchemaFromContext(final Bytes bytes) {
    return Optional.of(schema);
  }

  private static class EmptyContextDecoder implements RpcByteBufDecoder<Bytes> {

    @Override
    public Optional<Bytes> decodeOneMessage(final ByteBuf in) throws RpcException {
      return Optional.of(Bytes.EMPTY);
    }

    @Override
    public void complete() throws RpcException {
      // No-op
    }

    @Override
    public void close() {
      // No-op
    }
  }
}
