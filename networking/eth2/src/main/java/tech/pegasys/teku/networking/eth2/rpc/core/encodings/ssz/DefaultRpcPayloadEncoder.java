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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.InvalidSSZTypeException;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcPayloadEncoder;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class DefaultRpcPayloadEncoder<T> implements RpcPayloadEncoder<T> {
  private static final Logger LOG = LogManager.getLogger();
  private final Class<T> clazz;

  public DefaultRpcPayloadEncoder(final Class<T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public Bytes encode(final T message) {
    return SimpleOffsetSerializer.serialize((SimpleOffsetSerializable) message);
  }

  @Override
  public T decode(final Bytes message) throws RpcException {
    try {
      return SimpleOffsetSerializer.deserialize(message, clazz);
    } catch (final InvalidSSZTypeException e) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Failed to parse network message: " + message, e);
      }
      throw new DeserializationFailedException();
    }
  }

  @Override
  public boolean isLengthWithinBounds(final long length) {
    return SimpleOffsetSerializer.getLengthBounds(clazz).orElseThrow().isWithinBounds(length);
  }
}
