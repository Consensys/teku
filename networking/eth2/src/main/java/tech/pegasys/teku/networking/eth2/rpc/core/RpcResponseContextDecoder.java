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

package tech.pegasys.teku.networking.eth2.rpc.core;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.FixedSizeByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.ssz.type.Bytes4;

public interface RpcResponseContextDecoder<T> extends RpcByteBufDecoder<T> {
  RpcResponseContextDecoder<Bytes> NOOP = new EmptyContextDecoder();

  static RpcResponseContextDecoder<Bytes> noop() {
    return NOOP;
  }

  static RpcResponseContextDecoder<Bytes4> forkDigest() {
    return new ForkDigestContextDecoder();
  }

  class ForkDigestContextDecoder extends FixedSizeByteBufDecoder<Bytes4, RpcException>
      implements RpcResponseContextDecoder<Bytes4> {

    private ForkDigestContextDecoder() {
      super(4);
    }

    @Override
    protected Bytes4 decodeBytes(final Bytes bytes) {
      return new Bytes4(bytes);
    }

    @Override
    protected void throwUnprocessedDataException(final int dataLeft) throws RpcException {
      // Do nothing, exceptional case is handled upstream
    }
  }

  class EmptyContextDecoder implements RpcResponseContextDecoder<Bytes> {

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
