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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

class SszSnappyEncoding implements GossipEncoding {
  private static final String NAME = "ssz_snappy";
  private final SnappyBlockCompressor snappyCompressor;
  private final SszGossipCodec sszCodec = new SszGossipCodec();

  public SszSnappyEncoding(final SnappyBlockCompressor snappyCompressor) {
    this.snappyCompressor = snappyCompressor;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public <T extends SszData> Bytes encode(final T value) {
    return snappyCompressor.compress(sszCodec.encode(value));
  }

  @Override
  public <T extends SszData> T decodeMessage(PreparedGossipMessage message, SszSchema<T> valueType)
      throws DecodingException {
    if (!(message instanceof SnappyPreparedGossipMessage)) {
      throw new DecodingException("Unexpected PreparedMessage subclass: " + message.getClass());
    }
    SnappyPreparedGossipMessage lazyMessage = (SnappyPreparedGossipMessage) message;
    return sszCodec.decode(lazyMessage.getUncompressedOrThrow(), valueType);
  }

  @Override
  public <T extends SszData> PreparedGossipMessage prepareMessage(
      Bytes data, SszSchema<T> valueType) {
    return SnappyPreparedGossipMessage.create(data, valueType, snappyCompressor);
  }

  @Override
  public PreparedGossipMessage prepareUnknownMessage(Bytes data) {
    return SnappyPreparedGossipMessage.createUnknown(data);
  }
}
