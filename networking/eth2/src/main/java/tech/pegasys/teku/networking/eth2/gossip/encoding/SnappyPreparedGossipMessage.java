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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.datastructures.util.LengthBounds;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;

/**
 * {@link PreparedGossipMessage} implementation which calculates Gossip 'message-id' according to
 * Eth2 spec based on uncompressed gossip message payload: <code>
 *   SHA256(MESSAGE_DOMAIN_VALID_SNAPPY + snappy_decompress(message.data))[:20]
 * </code> The message payload is uncompressed lazily and cached for the final message handling:
 * {@link tech.pegasys.teku.networking.p2p.gossip.TopicHandler#handleMessage(PreparedGossipMessage)}
 */
class SnappyPreparedGossipMessage implements PreparedGossipMessage {
  // 4-byte domain for gossip message-id isolation of *invalid* snappy messages
  public static final Bytes MESSAGE_DOMAIN_INVALID_SNAPPY = Bytes.fromHexString("0x00000000");
  // 4-byte domain for gossip message-id isolation of *valid* snappy messages
  public static final Bytes MESSAGE_DOMAIN_VALID_SNAPPY = Bytes.fromHexString("0x01000000");

  private final Bytes compressedData;
  private final Class<?> valueType;
  private final SnappyBlockCompressor snappyCompressor;
  private final Supplier<Optional<Bytes>> uncompressed =
      Suppliers.memoize(this::maybeUncompressPayload);
  private DecodingException uncompressException;

  static SnappyPreparedGossipMessage createUnknown(Bytes compressedData) {
    return new SnappyPreparedGossipMessage(compressedData, null, null);
  }

  static SnappyPreparedGossipMessage create(
      Bytes compressedData, Class<?> valueType, SnappyBlockCompressor snappyCompressor) {
    return new SnappyPreparedGossipMessage(compressedData, valueType, snappyCompressor);
  }

  private SnappyPreparedGossipMessage(
      Bytes compressedData, Class<?> valueType, SnappyBlockCompressor snappyCompressor) {
    this.compressedData = compressedData;
    this.valueType = valueType;
    this.snappyCompressor = snappyCompressor;
  }

  public Bytes getUncompressedOrThrow() throws DecodingException {
    return getMaybeUncompressed()
        .orElseThrow(
            () -> new DecodingException("Couldn't uncompress the message", uncompressException));
  }

  public Optional<Bytes> getMaybeUncompressed() {
    return uncompressed.get();
  }

  private Optional<Bytes> maybeUncompressPayload() {
    try {
      if (valueType == null) {
        return Optional.empty();
      } else {
        return Optional.of(uncompressPayload());
      }
    } catch (DecodingException e) {
      uncompressException = e;
      return Optional.empty();
    }
  }

  private Bytes uncompressPayload() throws DecodingException {
    final LengthBounds lengthBounds =
        SimpleOffsetSerializer.getLengthBounds(valueType)
            .orElseThrow(() -> new DecodingException("Unknown message type: " + valueType));
    return snappyCompressor.uncompress(compressedData, lengthBounds);
  }

  @Override
  public Bytes getMessageId() {
    return Hash.sha2_256(
            getMaybeUncompressed()
                .map(
                    validSnappyUncompressed ->
                        Bytes.wrap(MESSAGE_DOMAIN_VALID_SNAPPY, validSnappyUncompressed))
                .orElse(Bytes.wrap(MESSAGE_DOMAIN_INVALID_SNAPPY, compressedData)))
        .slice(0, 20);
  }
}
