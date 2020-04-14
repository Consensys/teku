/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.networking.eth2.rpc.core.encodings.snappy;

import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.xerial.snappy.Snappy;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcException;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.LengthPrefixedEncoding;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcPayloadEncoder;

public class SnappyCompressor<T> implements RpcPayloadEncoder<T> {

  private final RpcPayloadEncoder<T> delegate;

  public SnappyCompressor(final RpcPayloadEncoder<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public Bytes encode(final T message) {
    final Bytes data = delegate.encode(message);
    try {
      return Bytes.wrap(Snappy.compress(data.toArrayUnsafe()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public T decode(final Bytes message) throws RpcException {
    final Bytes data;
    try {
      final byte[] dataArray = message.toArrayUnsafe();
      if (Snappy.uncompressedLength(dataArray) >= LengthPrefixedEncoding.MAX_CHUNK_SIZE) {
        throw RpcException.CHUNK_TOO_LONG_ERROR;
      }
      data = Bytes.wrap(Snappy.uncompress(dataArray));
    } catch (IOException e) {
      throw RpcException.MALFORMED_REQUEST_ERROR;
    }
    return delegate.decode(data);
  }
}
