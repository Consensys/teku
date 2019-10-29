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

package org.ethereum.beacon.discovery.packet;

import org.ethereum.beacon.discovery.Functions;
import org.ethereum.beacon.discovery.message.DiscoveryMessage;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpString;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes32s;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * Used when handshake is completed as a {@link DiscoveryMessage} authenticated container
 *
 * <p>Format:<code>
 * message-packet = tag || rlp_bytes(auth-tag) || message
 * message = aesgcm_encrypt(initiator-key, auth-tag, message-pt, tag)
 * message-pt = message-type || message-data</code>
 */
public class MessagePacket extends AbstractPacket {
  private MessagePacketDecoded decoded = null;
  private BytesValue initiatorKey = null;

  public MessagePacket(BytesValue bytes) {
    super(bytes);
  }

  public static MessagePacket create(
      Bytes32 homeNodeId,
      Bytes32 destNodeId,
      BytesValue authTag,
      BytesValue initiatorKey,
      DiscoveryMessage message) {
    BytesValue tag = Packet.createTag(homeNodeId, destNodeId);
    byte[] authTagBytesRlp = RlpEncoder.encode(RlpString.create(authTag.extractArray()));
    BytesValue authTagEncoded = BytesValue.wrap(authTagBytesRlp);
    BytesValue encryptedData =
        Functions.aesgcm_encrypt(initiatorKey, authTag, message.getBytes(), tag);
    return new MessagePacket(tag.concat(authTagEncoded).concat(encryptedData));
  }

  public void verify(BytesValue expectedAuthTag) {}

  public Bytes32 getHomeNodeId(Bytes32 destNodeId) {
    verifyDecode();
    return Bytes32s.xor(Functions.hash(destNodeId), decoded.tag);
  }

  public BytesValue getAuthTag() {
    if (decoded == null) {
      return BytesValue.wrap(
          ((RlpString)
                  RlpDecoder.decode(getBytes().slice(32, 13).extractArray()).getValues().get(0))
              .getBytes());
    }
    return decoded.authTag;
  }

  public DiscoveryMessage getMessage() {
    verifyDecode();
    return decoded.message;
  }

  private void verifyDecode() {
    if (decoded == null) {
      throw new RuntimeException("You should decode packet at first!");
    }
  }

  public void decode(BytesValue initiatorKey) {
    if (decoded != null) {
      return;
    }
    MessagePacketDecoded blank = new MessagePacketDecoded();
    blank.tag = Bytes32.wrap(getBytes().slice(0, 32), 0);
    blank.authTag =
        BytesValue.wrap(
            ((RlpString)
                    RlpDecoder.decode(getBytes().slice(32, 13).extractArray()).getValues().get(0))
                .getBytes());
    blank.message =
        new DiscoveryV5Message(
            Functions.aesgcm_decrypt(initiatorKey, blank.authTag, getBytes().slice(45), blank.tag));
    this.decoded = blank;
  }

  @Override
  public String toString() {
    if (decoded != null) {
      return "MessagePacket{"
          + "tag="
          + decoded.tag
          + ", authTag="
          + decoded.authTag
          + ", message="
          + decoded.message
          + '}';
    } else {
      return "MessagePacket{" + getBytes() + '}';
    }
  }

  private static class MessagePacketDecoded {
    private Bytes32 tag;
    private BytesValue authTag;
    private DiscoveryMessage message;
  }
}
