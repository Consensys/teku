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
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.AbstractByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;

public abstract class RpcResponseContextDecoder extends AbstractByteBufDecoder<Bytes, RpcException>
    implements RpcByteBufDecoder<Bytes> {
  public static RpcResponseContextDecoder noop() {
    return fixedSize(0);
  }

  public static RpcResponseContextDecoder bytes4() {
    return fixedSize(4);
  }

  private static RpcResponseContextDecoder fixedSize(final int size) {
    return new FixedSizeRpcResponseContextDecoder(size);
  }

  private static class FixedSizeRpcResponseContextDecoder extends RpcResponseContextDecoder {
    private final int fixedSize;

    public FixedSizeRpcResponseContextDecoder(final int size) {
      this.fixedSize = size;
    }

    @Override
    protected void throwUnprocessedDataException(final int dataLeft) throws RpcException {
      // Do nothing, exceptional case is handled upstream
    }

    @Override
    protected Optional<Bytes> decodeOneImpl(final ByteBuf in) throws RpcException {
      if (in.readableBytes() < fixedSize) {
        return Optional.empty();
      }
      final byte[] bytes = new byte[fixedSize];
      in.readBytes(bytes);
      return Optional.of(Bytes.of(bytes));
    }
  }
}
