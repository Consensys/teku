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
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.ssz.schema.SszSchema;

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

  private final String topic;
  private final Bytes compressedData;
  private final SszSchema<?> valueType;
  private final SnappyBlockCompressor snappyCompressor;
  private final Supplier<DecodedMessageResult> decodedResult =
      Suppliers.memoize(this::getDecodedMessage);

  static SnappyPreparedGossipMessage createUnknown(final String topic, final Bytes compressedData) {
    return new SnappyPreparedGossipMessage(topic, compressedData, null, null);
  }

  static SnappyPreparedGossipMessage create(
      final String topic,
      Bytes compressedData,
      SszSchema<?> valueType,
      SnappyBlockCompressor snappyCompressor) {
    return new SnappyPreparedGossipMessage(topic, compressedData, valueType, snappyCompressor);
  }

  private SnappyPreparedGossipMessage(
      final String topic,
      Bytes compressedData,
      SszSchema<?> valueType,
      SnappyBlockCompressor snappyCompressor) {
    this.topic = topic;
    this.compressedData = compressedData;
    this.valueType = valueType;
    this.snappyCompressor = snappyCompressor;
  }

  @Override
  public DecodedMessageResult getDecodedMessage() {
    try {
      if (valueType == null) {
        return DecodedMessageResult.failed();
      } else {
        final Bytes decodedMessage = uncompressPayload();
        return DecodedMessageResult.successful(decodedMessage);
      }
    } catch (DecodingException e) {
      return DecodedMessageResult.failed(e);
    }
  }

  private Optional<Bytes> getUncompressed() {
    return decodedResult.get().getDecodedMessage();
  }

  private Bytes uncompressPayload() throws DecodingException {
    return snappyCompressor.uncompress(compressedData, valueType.getSszLengthBounds());
  }

  @Override
  public Bytes getMessageId() {
    return Hash.sha2_256(
            getUncompressed()
                .map(
                    validSnappyUncompressed ->
                        Bytes.wrap(MESSAGE_DOMAIN_VALID_SNAPPY, validSnappyUncompressed))
                .orElse(Bytes.wrap(MESSAGE_DOMAIN_INVALID_SNAPPY, compressedData)))
        .slice(0, 20);
  }
}
